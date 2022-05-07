package escenario1

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import com.github.nscala_time.time.Imports.{richReadableInstant, richReadableInterval}
import escenario1.Basico.{Estacion, Localizacion, Paquete}
import escenario1.App.system
import org.joda.time.DateTime
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json._

import java.util.NoSuchElementException
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random
import scala.util.control.Breaks.{break, breakable}

/**
 * @author José Antonio Antona Díaz
 */

object Tren {
  case class  IniciarTren(id: Int, capacidad: Int, estaciones: Seq[Estacion], ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef, producerRef: ActorRef)
  case class  RecibirPaquetes (listaPaquetes: Seq[Paquete])
  case class  LocalizacionActual(localizacionActual: Unit)
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

  def intervaloTiempoTren(evento: String, tren_id: Int, capacidad: Int, coordenadasOrigen: Array[String], coordenadasDestino: Array[String], ruta: Seq[Localizacion], fdv: Int, fabMasterRef: ActorRef): Cancellable = {
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
        //Duracion del viaje. Realiza petición get al servidor, parsea el resultado a formato JSON, se obtiene la duración y se formatea a entero
        val peticionViaje = requests.get("http://127.0.0.1:5000/route/v1/driving/" + coordenadasOrigen(2) + "," + coordenadasOrigen(3) + ";" + coordenadasDestino(2) + "," + coordenadasDestino(3)).text()
        val jsonViaje: JsValue = Json.parse(
          s"""
          $peticionViaje
          """)
        val duracionViajeJsValue = (((jsonViaje \ "routes") (0) \ "legs") (0) \ "duration").get
        val duracionViaje = (s"$duracionViajeJsValue".toDouble * 1000 / fdv).round
        val segundosRealesViaje = (s"$duracionViajeJsValue".toDouble % 60).round.toInt
        val minutosRealesViaje = ((math floor s"$duracionViajeJsValue".toDouble / 60) % 60).toInt
        val horasRealesViaje = (math floor s"$duracionViajeJsValue".toDouble / 3600).toInt

        //Tiempo de aparición de logs periodicos para marcar lugar actual del tren en el trayecto
        val tiempoLogSegundos = 60 * 5 ////5 minutos
        val tiempoLog = (60 * 5 * 1000) / fdv

        //Peticion al servidor OSRM para obtener la ruta del tren
        //val simpleRequest = requests.get("https://jsonplaceholder.typicode.com/posts/1").text()
        val peticionViajeCompleto = requests.get("http://127.0.0.1:5000/route/v1/driving/" + coordenadasOrigen(2) + "," + coordenadasOrigen(3) + ";" + coordenadasDestino(2) + "," + coordenadasDestino(3) + "?steps=true").text()
        //Convertir respuesta del GET.text(), en formato string, a JSON para poder acceder a sus valores
        val jsonViajeCompleto: JsValue = Json.parse(
          s"""
          $peticionViajeCompleto
          """)

        val duracionManiobrasJsValue = (jsonViajeCompleto \\ "duration").toList
        val (duracionManiobrasJsValueLimpio, z) = duracionManiobrasJsValue.splitAt(duracionManiobrasJsValue.size - 2)
        val duracionManiobras: List[Double] = duracionManiobrasJsValueLimpio.map(duracion => s"$duracion".toDouble)
        var acDuracionManiobras: List[Double] = List(0)
        var acumulacion: Double = 0.0
        for (i <- 0 to (duracionManiobras.size - 1)) {
          acumulacion = acumulacion + duracionManiobras(i)
          acDuracionManiobras = acDuracionManiobras.+:(acumulacion)
        }
        val acumDuracionManiobras = acDuracionManiobras.reverse.map(double => BigDecimal(double).setScale(1, BigDecimal.RoundingMode.HALF_UP).toDouble).tail

        var indManiobras: List[Int] = List(0)
        for (i <- 1 to (duracionViaje / tiempoLog).toInt) {
          breakable {
            for (j <- 0 until acumDuracionManiobras.size) {
              if (acumDuracionManiobras(j) == tiempoLogSegundos * i) {
                indManiobras = indManiobras.+:(j + 1)
                break()
              }
              else if (acumDuracionManiobras(j) > tiempoLogSegundos * i) {
                indManiobras = indManiobras.+:(j)
                break()
              }
            }
          }
        }
        val indicesManiobras = indManiobras.reverse.tail

        val nombreLocalizacionManiobras = (jsonViajeCompleto \\ "name").toList
        val (nombreLocalizacionManiobrasLimpio, zz) = nombreLocalizacionManiobras.splitAt(nombreLocalizacionManiobras.size - 2)
        val nombreLocManiobras: List[String] = nombreLocalizacionManiobrasLimpio.map(nombre => s"$nombre")

        var refActual: List[String] = List("0")
        for (i <- 0 to nombreLocManiobras.size - 1) {
          try {
            val referencia = ((((jsonViajeCompleto \ "routes") (0) \ "legs") (0) \ "steps") (i) \ "ref").get
            refActual = refActual.+:(s"$referencia")
          } catch {
            case e: NoSuchElementException => refActual = refActual.+:("")
          }
        }
        val referenciaActual = refActual.reverse.tail

        var locActual: List[String] = List("Inicial")
        for (i <- 0 to indicesManiobras.size - 1) {
          if (nombreLocManiobras(indicesManiobras(i)).compareTo("") == 2) {
            if (referenciaActual(indicesManiobras(i)).compareTo("") == 0) {
              if (i != 0) locActual = locActual.+:("Referencia vía de circulación: " + referenciaActual(indicesManiobras(i - 1)))
            }
            else locActual = locActual.+:("Referencia vía de circulación: " + referenciaActual(indicesManiobras(i)))
          } else {
            if (referenciaActual(indicesManiobras(i)).compareTo("") == 0) locActual = locActual.+:("Nombre vía de circulación: " + nombreLocManiobras(indicesManiobras(i)))
            else locActual = locActual.+:("Nombre vía de circulación: " + nombreLocManiobras(indicesManiobras(i)) + "; referencia: " + referenciaActual(indicesManiobras(i)))
          }
        }
        val localizacionActual = locActual.reverse.tail

        log.debug(s"    [Tren $tren_id] Tren con salida: ${coordenadasOrigen(1)}, ${coordenadasOrigen(0)}. Destino: ${coordenadasDestino(1)}, ${coordenadasDestino(0)}") //random number viaje $rnd
        log.debug(s"    [Tren $tren_id] Duración estimada del viaje: $horasRealesViaje horas, $minutosRealesViaje minutos y $segundosRealesViaje segundos")

        for(i <- 0 to localizacionActual.size - 1) {
          val minutos = (((tiempoLogSegundos*(i+1)).toDouble / 60) % 60).toInt
          val horas = (tiempoLogSegundos*(i+1).toDouble / 3600).toInt
          if (tiempoLog*i.milliseconds < duracionViaje.milliseconds) {
            context.system.scheduler.scheduleOnce((tiempoLog * (i+1)).milliseconds) {
              if (horas == 0) log.debug(s"    [Tren $tren_id] Tiempo de viaje: "+minutos+": minutos. Posición actual: "+localizacionActual(i))
              if (horas == 1) log.debug(s"    [Tren $tren_id] Tiempo de viaje: "+horas+" hora, "+minutos+": minutos. Posición actual: "+localizacionActual(i))
              else log.debug(s"    [Tren $tren_id] Tiempo de viaje: "+horas+" horas, "+minutos+": minutos. Posición actual: "+localizacionActual(i))
            }
          }
        }
        context.system.scheduler.scheduleOnce(duracionViaje.milliseconds) { //rnd.milliseconds
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
      scheduleTren = intervaloTiempoTren("recibirPaquetes",id, capacidad, coordenadasOrigen, coordenadasDestino, ruta, fdv, fabMasterRef)
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
      scheduleTren = intervaloTiempoTren("cargarDescargarPaquetes",id, capacidad, coordenadasOrigen, coordenadasDestino, ruta, fdv, fabMasterRef)
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
      scheduleTren = intervaloTiempoTren("esperaInicioViaje", id, capacidad, coordenadasOrigen, coordenadasDestino, ruta, fdv, fabMasterRef)
      context.become(enEsperaInicioViaje(id, capacidad, estaciones, coordenadasOrigen, coordenadasDestino, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enEsperaInicioViaje(id: Int, capacidad: Int, estaciones: Seq[Estacion], coordenadasOrigen: Array[String], coordenadasDestino: Array[String], listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
    case InicioViaje =>
      scheduleTren.cancel()
      var coorTrenOrigen = Array("Ciudad Tren Origen", "Estacion Tren Origen", "Longitud Tren Origen","Latitud Tren Origen")
      var coorTrenDestino = Array("Ciudad Tren Destino", "Estacion Tren Origen", "Longitud Tren Destino","Latitud Tren Destino")
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: SALIDA DESDE EL ORIGEN, Fecha y hora: $dtEvento")
      for (i <- estaciones.indices) {
        if (localizacionOrigen.name == estaciones(i).ciudad) coorTrenOrigen = Array(estaciones(i).ciudad, estaciones(i).nombreEstacion, estaciones(i).longitud, estaciones(i).latitud)
        if (localizacionDestino.name == estaciones(i).ciudad) coorTrenDestino = Array(estaciones(i).ciudad, estaciones(i).nombreEstacion, estaciones(i).longitud, estaciones(i).latitud)
      }
      listaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id}, "priority": ${p.prioridad}, "client": "${p.cliente.name}", "event_type": "TRAIN DEPARTURE FROM ORIGIN", "event_location": "${localizacionOrigen.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("viaje",id, capacidad, coorTrenOrigen, coorTrenDestino, ruta, fdv, fabMasterRef)
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
      scheduleTren = intervaloTiempoTren("esperaDescargaPaquetes",id, capacidad, coordenadasOrigen, coordenadasDestino, ruta, fdv, fabMasterRef)
      context.become(enDestinoSinDescarga(id, capacidad, estaciones, coordenadasOrigen, coordenadasDestino, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
    case LocalizacionActual(message: Unit) =>
      log.debug("Posición actual del tren: "+message)
  }

  def enDestinoSinDescarga(id: Int, capacidad: Int, estaciones: Seq[Estacion], coordenadasOrigen: Array[String], coordenadasDestino: Array[String], listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive =  {
    case InicioDescarga =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: INICIO DESCARGA, Fecha y hora: $dtEvento")
      listaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id}, "priority": ${p.prioridad}, "client": "${p.cliente.name}", "event_type": "START OF TRAIN UNLOADING", "event_location": "${localizacionDestino.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("cargarDescargarPaquetes",id, capacidad, coordenadasOrigen, coordenadasDestino, ruta, fdv, fabMasterRef)
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
      scheduleTren = intervaloTiempoTren("entregaAlmacen",id, capacidad, coordenadasOrigen, coordenadasDestino, ruta, fdv, fabMasterRef)
      context.become(enDestino(id, capacidad, estaciones, coordenadasOrigen, coordenadasDestino, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enDestino(id: Int, capacidad: Int, estaciones: Seq[Estacion], coordenadasOrigen: Array[String], coordenadasDestino: Array[String], listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
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
      scheduleTren = intervaloTiempoTren("recibirPaquetes", id, capacidadRestante, coordenadasOrigen, coordenadasDestino, nuevaRuta, fdv, fabMasterRef)
      context.become(enOrigen(id, nuevaListaPaquetesTren, capacidad, estaciones, coordenadasOrigen, coordenadasDestino, nuevaRuta.head, nuevaRuta(1), nuevaRuta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }
}


