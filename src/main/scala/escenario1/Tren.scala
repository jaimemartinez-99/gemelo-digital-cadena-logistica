package escenario1

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import com.github.nscala_time.time.Imports.{richReadableInstant, richReadableInterval}
import escenario1.Basico.{Localizacion, Paquete}
import escenario1.App.system
import org.joda.time.DateTime
import play.api.libs.json.JsValue.jsValueToJsLookup
import play.api.libs.json._

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

/**
 * @author José Antonio Antona Díaz
 */

object Tren {
  case class  IniciarTren(id: Int, capacidad: Int, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef, producerRef: ActorRef)
  case class  RecibirPaquetes (listaPaquetes: Seq[Paquete])
  case object FinCargaDescarga
  case object InicioViaje
  case object LocalizacionActual
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
  var periodicScheduler: ActorRef = context.system.actorOf(Props[Tren])

  def intervaloTiempoTren(evento: String, tren_id: Int, capacidad: Int, ruta: Seq[Localizacion], fdv: Int, fabMasterRef: ActorRef): Cancellable = {
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
        val rnd = (3600 * 5 + r.nextInt(3600 * 4)) * 1000 / fdv ////1hour = 3600seconds

        //Variable con el tiempo de aparición de logs periodicos para marcar lugar actual del tren en el trayecto
        val logTime = (60 * 5 * 1000) / fdv ////5 minutos

        //Get al servidor OSRM para obtener la ruta del tren
        val simpleRequest = requests.get("https://jsonplaceholder.typicode.com/posts/1").text()

        //Convertir respuesta del GET.text(), en formato string, a JSON para poder acceder a sus valores
        val jsonRequest: JsValue = Json.parse(s"""
          $simpleRequest
          """)
        val stringValue: String = (jsonRequest \ "title").as[JsString].value

        //Variable que inicia el scheduler periodico para marcar la posicion del tren
        val periodicRoutine: Cancellable = context.system.scheduler.scheduleWithFixedDelay(logTime milliseconds, logTime milliseconds , periodicScheduler, LocalizacionActual)
        log.debug(s"    [Tren $tren_id] random number viaje $rnd")
        context.system.scheduler.scheduleOnce(rnd.milliseconds) {
          log.debug(s"PRUEBA ACCESO JSON: $stringValue") // Prueba de acceso a uno de los valores del JSON
          periodicRoutine.cancel()
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
    case IniciarTren(id,capacidad,ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef, producerRef) =>
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Iniciado en ${ruta.head.name} con una capacidad maxima de $capacidad paquetes, Fecha y hora: $dtEvento")
      producer = producerRef
      scheduleTren = intervaloTiempoTren("recibirPaquetes",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enOrigen(id, Seq[Paquete](), capacidad, ruta.head, ruta(1), ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
    case LocalizacionActual =>
      log.debug("MENSAJE DE PRUEBA PROGRAMACION PERIODICA DE MENSAJES")
  }

  def enOrigen(id: Int, listaPaquetesTren: Seq[Paquete], capacidad: Int, localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
    case RecibirPaquetes (listaPaquetes) =>
      scheduleTren.cancel()
      val nuevaListaPaquetesTren = listaPaquetesTren ++ listaPaquetes
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: INICIO CARGA DEL TREN, Salida de paquetes: ${nuevaListaPaquetesTren.map(p => p.id)}, Origen: ${localizacionOrigen.name}, Destino: ${localizacionDestino.name}, Fecha y hora: $dtEvento")
      nuevaListaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id} , "priority": ${p.prioridad} , "client": "${p.cliente.name}", "event_type": "START OF TRAIN LOADING", "event_location": "${localizacionOrigen.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("cargarDescargarPaquetes",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enCarga(id, capacidad, nuevaListaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enCarga(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
    case FinCargaDescarga =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: FIN CARGA, Fecha y hora: $dtEvento")
      listaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id}, "priority": ${p.prioridad}, "client": "${p.cliente.name}", "event_type": "END OF TRAIN LOADING", "event_location": "${localizacionOrigen.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("esperaInicioViaje",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enEsperaInicioViaje(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))

  }

  def enEsperaInicioViaje(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
    case InicioViaje =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: SALIDA DESDE EL ORIGEN, Fecha y hora: $dtEvento")
      listaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id}, "priority": ${p.prioridad}, "client": "${p.cliente.name}", "event_type": "TRAIN DEPARTURE FROM ORIGIN", "event_location": "${localizacionOrigen.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("viaje",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enViaje(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enViaje(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive =  {
    case FinViaje =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: LLEGADA A DESTINO, Fecha y hora: $dtEvento")
      listaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id}, "priority": ${p.prioridad}, "client": "${p.cliente.name}", "event_type": "TRAIN ARRIVAL AT DESTINATION", "event_location": "${localizacionDestino.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("esperaDescargaPaquetes",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enDestinoSinDescarga(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enDestinoSinDescarga(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive =  {
    case InicioDescarga =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: INICIO DESCARGA, Fecha y hora: $dtEvento")
      listaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id}, "priority": ${p.prioridad}, "client": "${p.cliente.name}", "event_type": "START OF TRAIN UNLOADING", "event_location": "${localizacionDestino.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("cargarDescargarPaquetes",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enDescarga(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enDescarga(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive =  {
    case FinCargaDescarga =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: FIN DESCARGA, Fecha y hora: $dtEvento")
      listaPaquetesTren.foreach(p =>
        producer ! f"""{"package_id": ${p.id}, "priority": ${p.prioridad}, "client": "${p.cliente.name}", "event_type": "END OF TRAIN UNLOADING", "event_location": "${localizacionDestino.name}", "date": "$dtEvento", "train_origin": "${localizacionOrigen.name}", "train_id": $id, "train_destination": "${localizacionDestino.name}"}"""
      )
      scheduleTren = intervaloTiempoTren("entregaAlmacen",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enDestino(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enDestino(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
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
      scheduleTren = intervaloTiempoTren("recibirPaquetes", id, capacidadRestante, nuevaRuta, fdv, fabMasterRef)
      context.become(enOrigen(id, nuevaListaPaquetesTren, capacidad, nuevaRuta.head, nuevaRuta(1), nuevaRuta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }
}


