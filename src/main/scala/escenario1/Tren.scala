package escenario1

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import com.github.nscala_time.time.Imports.{richReadableInstant, richReadableInterval}
import escenario1.Basico.{Estacion, Localizacion, Paquete}
import escenario1.App.system
import org.joda.time.DateTime
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json._

//import java.util.NoSuchElementException
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random
import scala.util.control.Breaks.{break, breakable}

/**
 * @author José Antonio Antona Díaz
 * @author Mario Esperalta Delgado
 */

object Tren {
  case class  IniciarTren(id: Int, capacidad: Int, estaciones: Seq[Estacion], ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef, producerRef: ActorRef)
  case class  RecibirPaquetes (listaPaquetes: Seq[Paquete])
  case object FinCargaDescarga
  case object InicioViaje
  case object FinViaje
  case object InicioDescarga
  case object EntregarAlmacen
}

class Tren extends Actor with ActorLogging {
  import Tren._
  import FabricaMaster._
  import AlmacenMaster._
  import system.dispatcher

  var scheduleTren: Cancellable = _
  var producer: ActorRef = _

  def intervaloTiempoTren(evento: String, tren_id: Int, capacidad: Int, coordenadasOrigen: Array[String], coordenadasDestino: Array[String], ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef): Cancellable = {
    val r = new Random()
    evento match {
      case "recibirPaquetes" =>
        val rnd = (86400 + r.nextInt(86400 * 5)) * 1000 / fdv
        //1day = 86400seconds
        log.debug(s"    [Tren $tren_id] random number recibir $rnd")
        context.system.scheduler.scheduleOnce(rnd.milliseconds) {
          fabMasterRef ! SalidaPaquetesMaster(capacidad, ruta)
        }
      case "cargarDescargarPaquetes" =>
        val rnd = (3600 * 2 + r.nextInt(3600 * 6)) * 1000 / fdv
        //1hour = 3600seconds
        log.debug(s"    [Tren $tren_id] random number cargar/descargar $rnd")
        context.system.scheduler.scheduleOnce(rnd.milliseconds) {
          self ! FinCargaDescarga
        }
      case "viaje" =>
        //Duración del viaje
        //Petición GET al servidor para obtener la ruta con la duración del viaje
        val peticionViaje = requests.get("http://127.0.0.1:5000/route/v1/train/" + coordenadasOrigen(2) + "," + coordenadasOrigen(3) + ";" + coordenadasDestino(2) + "," + coordenadasDestino(3)).text()
        //Parseo  de la respuesta de la petición a formato JSON para poder ser accesible
        val jsonViaje: JsValue = Json.parse(
          s"""
          $peticionViaje
          """)
        //Obtención del campo "duration" de la respuesta con la duracion del viaje
        //Se cambia el tipo del valor de JsValue a valor numérico Double (segundos)
        //Se transforma en horas, minutos y segundos
        //VÁLIDO PARA VIAJES CON SOLO UNA COORDENADA DE ORIGEN Y OTRA DESTINO (Caso actual)
        //SI LA PETICIÓN ES DE VARIAS COORDENADAS, HAY QUE OBSERVAR LA RESPUESTA DE LA PETICIÓN Y VER QUÉ CAMPO 'DURATION' ES EL DE LA DURACIÓN COMPLETA DEL VIAJE
        val duracionViajeJsValue = (((jsonViaje \ "routes") (0) \ "legs") (0) \ "duration").get
        val duracionViajeReal = s"$duracionViajeJsValue".toDouble
        val duracionViaje = duracionViajeReal * 1000 / fdv.toDouble
        val segundosRealesViaje = (duracionViajeReal % 60).round.toInt
        val minutosRealesViaje = ((math floor duracionViajeReal / 60) % 60).toInt
        val horasRealesViaje = (math floor duracionViajeReal / 3600).toInt

        //Tiempo de aparición de los logs periódicos marcando la posición actual del tren
        val tiempoLogSegundos = 60 * 5 ////5 minutos
        val tiempoLog = (tiempoLogSegundos.toDouble * 1000) / fdv.toDouble

        //Coordenadas de la posición actual del tren durante el trayectode la localización actual del vehículo en el trayecto
        //TAMBIÉN CUENTA CON EL NOMBRE DE LA VÍA DE CIRCULACIÓN ACTUAL DEL VEHÍCULO. VÁLIDO PARA TRAYECTOS INTERNACIONALES Y PARA EL PERFIL OSRM DE COCHE U OTRO DE CARRETERA. DESCOMENTAR CÓDIGO SI SE USA PARA ESE FIN.
        //
        //Petición GET al servidor para obtener la ruta con información más detallada (Cordenadas registrables con conforman el trayecto, maniobras realizadas durante el viaje, duración del trayecto entre maniobras, nombre de la vía de comunicación por la que pasa el vehículo...)
        val peticionViajeCompleto = requests.get("http://127.0.0.1:5000/route/v1/train/" + coordenadasOrigen(2) + "," + coordenadasOrigen(3) + ";" + coordenadasDestino(2) + "," + coordenadasDestino(3) + "?overview=full&geometries=geojson&steps=true").text()
        val jsonViajeCompleto: JsValue = Json.parse(
          s"""
          $peticionViajeCompleto
          """)
        //Lista con todas las coordenadas registrables por las que pasa el tren durante el trayecto
        //Comienza con la siguiente a la del origen (la coordenada registrable más cercana al origen) y acaba en la de destino.
        val coordenadasViajeTren = ((jsonViajeCompleto \ "routes") (0) \ "geometry" \ "coordinates").get.as[List[List[Double]]]
        //Tiempo que hay entre cada par de coordenadas registrables del viaje
        //Como en el perfil de trenes calculado para OSRM el tren va a velocidad constante, el tiempo entre coordenadas también lo será. En otro tipo de perfil de OSRM, es una aproximación a velocidad constante del medio de transporte
        val tiempoViajeEntreCoordenadas = BigDecimal(duracionViajeReal/coordenadasViajeTren.size).toDouble
        //Lista con la duración que hay entre el origen y cada coordenada registable del viaje
        var tACoordenada: List[Double] = List(tiempoViajeEntreCoordenadas)
        for (i <- 2 to coordenadasViajeTren.size) {
          tACoordenada = tACoordenada.+:(tiempoViajeEntreCoordenadas*i)
        }
        val tiempoACoordenada = tACoordenada.reverse
        //Nombre de la vía de circulación actual del vehículo durante el viaje
        //
        /*
        //El nombre de la vía por la que pasa el vehiculo se obtiene del nombre del lugar donde se realizan las maniobras registrables en OSRM durante el trayecto
        //Lista con la duración acumulativa que hay desde el origen hasta cada maniobra realizada
        val duracionManiobrasJsValue = (jsonViajeCompleto \\ "duration").toList
        val (duracionManiobrasJsValueLimpio, _) = duracionManiobrasJsValue.splitAt(duracionManiobrasJsValue.size - 2)
        val duracionManiobras: List[Double] = duracionManiobrasJsValueLimpio.map(duracion => BigDecimal(s"$duracion").toDouble)
        var acDuracionManiobras: List[Double] = List(duracionManiobras.head)
        var acumulacion: Double = 0.0
        for (i <- 1 until duracionManiobras.size) {
          acumulacion = acumulacion + duracionManiobras(i)
          acDuracionManiobras = acDuracionManiobras.+:(acumulacion)
        }
        val acumDuracionManiobras = acDuracionManiobras.reverse.map(double => BigDecimal(double).toDouble)
        */
        //Lista con los índices de la lista de tiempos a cada coordenada que se correspondan con el tiempo de los logs periódicos del programa o que más se acerque
        //El tamaño de la lista es la cantidad de logs que se harían durante el viaje
        //Además, se crea otra lista de las mismas características para la lista de duración a maniobras
        var indCoordenada: List[Int] = List(0)
        //var indManiobras: List[Int] = List(0)
        for (i <- 1 to (duracionViaje / tiempoLog).toInt) {
          breakable {
            for (j <- tiempoACoordenada.indices) {
              if (tiempoACoordenada(j) == tiempoLogSegundos * i) {
                indCoordenada = indCoordenada.+:(j)
                break()
              }
              else if (tiempoACoordenada(j) > tiempoLogSegundos * i) {
                indCoordenada = indCoordenada.+:(j)
                break()
              }
            }
          }
          /*
          breakable {
            for (j <- acumDuracionManiobras.indices) {
              if (acumDuracionManiobras(j) == tiempoLogSegundos * i) {
                indManiobras = indManiobras.+:(j)
                break()
              }
              else if (acumDuracionManiobras(j) > tiempoLogSegundos * i) {
                indManiobras = indManiobras.+:(j)
                break()
              }
            }
          }
          */
        }
        val indicesCoordenadas = indCoordenada.reverse.tail
        //val indicesManiobras = indManiobras.reverse.tail
        /*
        //Lista con los nombres de la vía de comunicación donde se realizan las maniobras
        val nombreLocalizacionManiobras = (jsonViajeCompleto \\ "name").toList
        val (nombreLocalizacionManiobrasLimpio, _) = nombreLocalizacionManiobras.splitAt(nombreLocalizacionManiobras.size - 2)
        val nombreLocManiobras: List[String] = nombreLocalizacionManiobrasLimpio.map(nombre => s"$nombre")
        //Lista con la referencia de la vía de comunicación donde se realizan las maniobras
        //Es un acompañamiento al nombre y puede hacer de él en caso de que no exista
        //Como hay maniobras en las que no aparece esta propiedad ("ref") hay que añadírsela en blanco a cada una de ellas
        //En ocasiones, el campo de referencia "ref" no existe para ciertas maniobras. Por ello, cada vez que no exista hay que añadirlas en blanco
        var refActual: List[String] = List("0")
        for (i <- nombreLocManiobras.indices) {
          try {
            val referencia = ((((jsonViajeCompleto \ "routes") (0) \ "legs") (0) \ "steps") (i) \ "ref").get
            refActual = refActual.+:(s"$referencia")
          } catch {
            case _: NoSuchElementException => refActual = refActual.+:("")
          }
        }
        val referenciaActual = refActual.reverse.tail
        //Lista de la localización actual del vehículo
        //Está formada por el nombre y la referencia de la vía de comunicación, en caso de que exista, en la que se encuentre el vehículo
        //Se eligen las maniobras más cercanas al tiempo de los logs periódicos a través de la lista "indicesManiobras"
        //Se obtiene la lista de la localización actual (nombre vía) del tren en la cantidad de tiempo marcada por "tiempoLog"
        var locActual: List[String] = List("Inicial")
        for (i <- indicesManiobras.indices) {
          if (nombreLocManiobras(indicesManiobras(i)).compareTo("") == 2) {
            if (referenciaActual(indicesManiobras(i)).compareTo("") == 0) {
              if (i != 0) locActual = locActual.+:("Referencia vía de circulación: " + referenciaActual(indicesManiobras(i - 1)))
              else locActual = locActual.+:("No se puede referenciar el nombre de la vía")
            }
            else locActual = locActual.+:("Referencia vía de circulación: " + referenciaActual(indicesManiobras(i)))
          } else {
            if (referenciaActual(indicesManiobras(i)).compareTo("") == 0) locActual = locActual.+:("Nombre vía de circulación: " + nombreLocManiobras(indicesManiobras(i)))
            else locActual = locActual.+:("Nombre vía de circulación: " + nombreLocManiobras(indicesManiobras(i)) + "; referencia: " + referenciaActual(indicesManiobras(i)))
          }
        }
        val localizacionActual = locActual.reverse.tail
        */

        //Resumen de la ruta realizada entre las coordenadas origen y destino
        //Válido para cuando solamente hay una coordenada de origen y otra de destino
        //En futuras implementaciones, si se realizan rutas con varias coordenadas, el resultado de "summary" es una lista que debe ser tratada igual que los nombres de las vías.
        val resumenRutaJsValue = (jsonViajeCompleto \\ "summary").head
        val resumenRuta = s"$resumenRutaJsValue"

        log.debug(s"    [Tren $tren_id] Tren con salida: ${coordenadasOrigen(1)}, ${coordenadasOrigen(0)}. Destino: ${coordenadasDestino(1)}, ${coordenadasDestino(0)}") //random number viaje $rnd
        log.debug(s"    [Tren $tren_id] Duración estimada del viaje: $horasRealesViaje horas, $minutosRealesViaje minutos y $segundosRealesViaje segundos")
        log.debug(s"    [Tren $tren_id] Resumen ruta Origen-Destino: $resumenRuta")

        //Scheduler cada "tiempoLog" hasta el final del viaje con la posición actual del tren y el tiempo que lleva de viaje
        for(i <- indicesCoordenadas.indices) {
          val minutos = (((tiempoLogSegundos*(i+1)).toDouble / 60) % 60).toInt
          val horas = (tiempoLogSegundos*(i+1).toDouble / 3600).toInt
          if (tiempoLog*i < duracionViaje) {
            context.system.scheduler.scheduleOnce((tiempoLog * (i+1)).milliseconds) {
              val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
              if (horas == 0) {
                log.debug(s"    [Tren $tren_id] Tiempo de viaje --> "+minutos+" minutos || Posición actual --> Longitud: "+coordenadasViajeTren(indicesCoordenadas(i)).head+", Latitud: "+coordenadasViajeTren(indicesCoordenadas(i))(1))
              } //+"; "+localizacionActual(i))
              if (horas == 1) {
                log.debug(s"    [Tren $tren_id] Tiempo de viaje --> "+horas+" hora, "+minutos+": minutos || Posición actual --> Longitud: "+coordenadasViajeTren(indicesCoordenadas(i)).head+", Latitud: "+coordenadasViajeTren(indicesCoordenadas(i))(1))
                val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
              } //+";  "+localizacionActual(i))
              if (horas > 1) {
                log.debug(s"    [Tren $tren_id] Tiempo de viaje --> "+horas+" horas, "+minutos+" minutos || Posición actual --> Longitud: "+coordenadasViajeTren(indicesCoordenadas(i)).head+", Latitud: "+coordenadasViajeTren(indicesCoordenadas(i))(1))
                val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
              } //+";  "+localizacionActual(i))
              producer ! f"""{"train_id": $tren_id, "event_type": "TRAIN IN TRAVEL", "date": "$dtEvento", "train_origin": "${coordenadasOrigen(0)}", "train_destination": "${coordenadasDestino(0)}", "station_origin": "${coordenadasOrigen(1)}", "station_destination": "${coordenadasDestino(1)}", "longitude_coordinate": ${coordenadasViajeTren(indicesCoordenadas(i)).head}, "latitude_coordinate": ${coordenadasViajeTren(indicesCoordenadas(i))(1)}}"""
            }
          }
        }
        context.system.scheduler.scheduleOnce(duracionViaje.milliseconds) {
          self ! FinViaje
        }
      case "esperaInicioViaje" =>
        val rnd = (3600 + r.nextInt(3600*2))*1000 / fdv
        //1hour = 3600seconds
        log.debug(s"    [Tren $tren_id] random number espera inicio viaje $rnd")
        context.system.scheduler.scheduleOnce(rnd.milliseconds){
          self ! InicioViaje
        }
      case "esperaDescargaPaquetes" =>
        val rnd = (3600 + r.nextInt(3600*2))*1000 / fdv
        //1hour = 3600seconds
        log.debug(s"    [Tren $tren_id] random number espera descarga $rnd")
        context.system.scheduler.scheduleOnce(rnd.milliseconds){
          self ! InicioDescarga
        }
      case "entregaAlmacen" =>
        val rnd = (3600 + r.nextInt(3600*2))*1000 / fdv
        //1hour = 3600seconds
        log.debug(s"    [Tren $tren_id] random number entrega almacen $rnd")
        context.system.scheduler.scheduleOnce(rnd.milliseconds){
          self ! EntregarAlmacen
        }
    }
  }

  override def receive: Receive =  {
    case IniciarTren(id, capacidad, estaciones, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef, producerRef) =>
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      val coordenadasOrigen = Array("Ciudad Origen","Estacion Origen","Longitud Origen", "Latitud Origen")
      val coordenadasDestino =  Array("Ciudad Origen","Estacion Origen","Longitud Destino", "Latitud Destino")
      log.debug(s"    [Tren $id] Iniciado en ${ruta.head.name} con una capacidad maxima de $capacidad paquetes, Fecha y hora: $dtEvento")
      producer = producerRef
      scheduleTren = intervaloTiempoTren("recibirPaquetes",id, capacidad, coordenadasOrigen, coordenadasDestino, ruta, fdv, dtI, dt0, fabMasterRef)
      context.become(enOrigen(id, Seq[Paquete](), capacidad, estaciones, coordenadasOrigen, coordenadasDestino, ruta.head, ruta(1), ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enOrigen(id: Int, listaPaquetesTren: Seq[Paquete], capacidad: Int, estaciones: Seq[Estacion], coordenadasOrigen: Array[String], coordenadasDestino: Array[String], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
    case RecibirPaquetes (listaPaquetes) =>
      scheduleTren.cancel()
      val nuevaListaPaquetesTren = listaPaquetesTren ++ listaPaquetes
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: INICIO CARGA DEL TREN, Salida de paquetes: ${nuevaListaPaquetesTren.map(p => p.id)}, Origen: ${localizacionOrigen.name}, Destino: ${localizacionDestino.name}, Fecha y hora: $dtEvento")
      nuevaListaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id} , "priority": ${p.prioridad} , "client": "${p.cliente.name}", "event_type": "START OF TRAIN LOADING", "event_location": "${localizacionOrigen.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("cargarDescargarPaquetes",id, capacidad, coordenadasOrigen, coordenadasDestino, ruta, fdv, dtI, dt0, fabMasterRef)
      context.become(enCarga(id, capacidad, estaciones, coordenadasOrigen, coordenadasDestino, nuevaListaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enCarga(id: Int, capacidad: Int, estaciones: Seq[Estacion], coordenadasOrigen: Array[String], coordenadasDestino: Array[String], listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
    case FinCargaDescarga =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: FIN CARGA, Fecha y hora: $dtEvento")
      listaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id}, "priority": ${p.prioridad}, "client": "${p.cliente.name}", "event_type": "END OF TRAIN LOADING", "event_location": "${localizacionOrigen.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("esperaInicioViaje", id, capacidad, coordenadasOrigen, coordenadasDestino, ruta, fdv, dtI, dt0, fabMasterRef)
      context.become(enEsperaInicioViaje(id, capacidad, estaciones, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enEsperaInicioViaje(id: Int, capacidad: Int, estaciones: Seq[Estacion], listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
    case InicioViaje =>
      scheduleTren.cancel()
      var coorTrenOrigen = Array("Ciudad Tren Origen", "Estacion Tren Origen", "Longitud Tren Origen","Latitud Tren Origen")
      var coorTrenDestino = Array("Ciudad Tren Destino", "Estacion Tren Origen", "Longitud Tren Destino","Latitud Tren Destino")
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: SALIDA DESDE EL ORIGEN, Fecha y hora: $dtEvento")

      // Coordenadas origen y destino del viaje en tren entre estaciones
      // Según cuál sea la ciudad de donde salen los paquetes y a cuál llegan, se coge una estación aleatoria de esa ciudad de entre las disponibles en la variable "estaciones"
      // Si sólo hay una estación para una ciudad, siempre se coge esa
      // Se crean dos listas con las posiciones de las posibles estaciones para elegir dentro de la variable "estaciones"
      var posEstacionesOrigen: List[Int] = List(0)
      var posEstacionesDestino: List[Int] = List(0)
      for (i <- estaciones.indices) {
        if (estaciones(i).ciudad.contains(localizacionOrigen.name)) {
          posEstacionesOrigen = posEstacionesOrigen.+:(i)
        }
        if (estaciones(i).ciudad.contains(localizacionDestino.name)) {
          posEstacionesDestino = posEstacionesDestino.+:(i)
        }
      }
      val posicionesEstacionesOrigen = posEstacionesOrigen.reverse.tail
      val posicionesEstacionesDestino = posEstacionesDestino.reverse.tail
      // Dentro de cada lista, se coge una de las posiciones de forma aleatoria
      val r = scala.util.Random
      val posEstacionAletoriaOrigen = posicionesEstacionesOrigen(r.nextInt(posicionesEstacionesOrigen.size))
      val posEstacionAletoriaDestino = posicionesEstacionesDestino(r.nextInt(posicionesEstacionesDestino.size))
      // Se asigna la estación que toque según la posición elegida a las variables coorTrenOrigen y coorTrenDestino
      coorTrenOrigen = Array(estaciones(posEstacionAletoriaOrigen).ciudad, estaciones(posEstacionAletoriaOrigen).nombreEstacion, estaciones(posEstacionAletoriaOrigen).longitud, estaciones(posEstacionAletoriaOrigen).latitud)
      coorTrenDestino = Array(estaciones(posEstacionAletoriaDestino).ciudad, estaciones(posEstacionAletoriaDestino).nombreEstacion, estaciones(posEstacionAletoriaDestino).longitud, estaciones(posEstacionAletoriaDestino).latitud)

      listaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id}, "priority": ${p.prioridad}, "client": "${p.cliente.name}", "event_type": "TRAIN DEPARTURE FROM ORIGIN", "event_location": "${localizacionOrigen.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("viaje",id, capacidad, coorTrenOrigen, coorTrenDestino, ruta, fdv, dtI, dt0, fabMasterRef)
      context.become(enViaje(id, capacidad, estaciones, coorTrenOrigen, coorTrenDestino, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enViaje(id: Int, capacidad: Int, estaciones: Seq[Estacion], coordenadasOrigen: Array[String], coordenadasDestino: Array[String], listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive =  {
    case FinViaje =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: LLEGADA A DESTINO, Fecha y hora: $dtEvento")
      listaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id}, "priority": ${p.prioridad}, "client": "${p.cliente.name}", "event_type": "TRAIN ARRIVAL AT DESTINATION", "event_location": "${localizacionDestino.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("esperaDescargaPaquetes",id, capacidad, coordenadasOrigen, coordenadasDestino, ruta, fdv, dtI, dt0, fabMasterRef)
      context.become(enDestinoSinDescarga(id, capacidad, estaciones, coordenadasOrigen, coordenadasDestino, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enDestinoSinDescarga(id: Int, capacidad: Int, estaciones: Seq[Estacion], coordenadasOrigen: Array[String], coordenadasDestino: Array[String], listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive =  {
    case InicioDescarga =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: INICIO DESCARGA, Fecha y hora: $dtEvento")
      listaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id}, "priority": ${p.prioridad}, "client": "${p.cliente.name}", "event_type": "START OF TRAIN UNLOADING", "event_location": "${localizacionDestino.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("cargarDescargarPaquetes",id, capacidad, coordenadasOrigen, coordenadasDestino, ruta, fdv, dtI, dt0, fabMasterRef)
      context.become(enDescarga(id, capacidad, estaciones, coordenadasOrigen, coordenadasDestino, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enDescarga(id: Int, capacidad: Int, estaciones: Seq[Estacion], coordenadasOrigen: Array[String], coordenadasDestino: Array[String], listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive =  {
    case FinCargaDescarga =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: FIN DESCARGA, Fecha y hora: $dtEvento")
      listaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id}, "priority": ${p.prioridad}, "client": "${p.cliente.name}", "event_type": "END OF TRAIN UNLOADING", "event_location": "${localizacionDestino.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("entregaAlmacen",id, capacidad, coordenadasOrigen, coordenadasDestino, ruta, fdv, dtI, dt0, fabMasterRef)
      context.become(enDestino(id, capacidad, estaciones, coordenadasOrigen, coordenadasDestino, listaPaquetesTren, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enDestino(id: Int, capacidad: Int, estaciones: Seq[Estacion], coordenadasOrigen: Array[String], coordenadasDestino: Array[String], listaPaquetesTren: Seq[Paquete], localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
    case EntregarAlmacen =>
      scheduleTren.cancel()
      var listaPaquetesParaAlmacen = Seq[Paquete]()
      listaPaquetesTren.foreach(p =>
        if(p.localizacionDestino == localizacionDestino){
          listaPaquetesParaAlmacen = listaPaquetesParaAlmacen :+ p
        }
      )
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"listaPaquetes para almacen: ${listaPaquetesParaAlmacen.map(p=>p.id)}")
      almMasterRef ! RecibirPaquetesAlmacenMaster(listaPaquetesParaAlmacen, localizacionDestino)

      val nuevaRuta: Seq[Localizacion] = ruta.tail :+ ruta.head
      val nuevaListaPaquetesTren = listaPaquetesTren.diff(listaPaquetesParaAlmacen)
      val capacidadRestante = capacidad - nuevaListaPaquetesTren.size
      log.debug(s"    [Tren $id] En ${nuevaRuta.head.name} con una capacidad maxima de $capacidad paquetes y los paquetes: ${nuevaListaPaquetesTren.map(p => p.id)}, Fecha y hora: $dtEvento")
      scheduleTren = intervaloTiempoTren("recibirPaquetes", id, capacidadRestante, coordenadasOrigen, coordenadasDestino, nuevaRuta, fdv, dtI, dt0, fabMasterRef)
      context.become(enOrigen(id, nuevaListaPaquetesTren, capacidad, estaciones, coordenadasOrigen, coordenadasDestino, nuevaRuta.head, nuevaRuta(1), nuevaRuta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }
}