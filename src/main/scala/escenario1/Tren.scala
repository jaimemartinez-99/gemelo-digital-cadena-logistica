package escenario1

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import com.github.nscala_time.time.Imports.{richReadableInstant, richReadableInterval}
import escenario1.Basico.{Localizacion, Paquete}
import escenario1.App.system
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.util.Random

/**
 * Tren
 */

object Tren {
  case class  IniciarTren(id: Int, capacidad: Int, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef)
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

  def intervaloTiempoTren(evento: String, tren_id: Int, capacidad: Int, ruta: Seq[Localizacion], fdv: Int, fabMasterRef: ActorRef): Cancellable = {
    val r = new Random()
    evento match {
      case "recibirPaquetes" =>
        val rnd = (30 + r.nextInt(10)) / fdv
        log.debug(s"    [Tren $tren_id] random number recibir $rnd")
        context.system.scheduler.scheduleOnce(rnd.seconds){
          fabMasterRef ! SalidaPaquetesMaster(capacidad, ruta)
        }
      case "cargarDescargarPaquetes" =>
        val rnd = (5 + r.nextInt(5)) / fdv
        log.debug(s"    [Tren $tren_id] random number cargar/descargar $rnd")
        context.system.scheduler.scheduleOnce(rnd.seconds){
          self ! FinCargaDescarga
        }
      case "viaje" =>
        val rnd = (30 + r.nextInt(10)) / fdv
        log.debug(s"    [Tren $tren_id] random number viaje $rnd")
        context.system.scheduler.scheduleOnce(rnd.seconds){
          self ! FinViaje
        }
      case "esperaInicioViaje" =>
        val rnd = (5 + r.nextInt(5))/ fdv
        log.debug(s"    [Tren $tren_id] random number espera inicio viaje $rnd")
        context.system.scheduler.scheduleOnce(rnd.seconds){
          self ! InicioViaje
        }
      case "esperaDescargaPaquetes" =>
        val rnd = (5 + r.nextInt(5)) / fdv
        log.debug(s"    [Tren $tren_id] random number espera descarga $rnd")
        context.system.scheduler.scheduleOnce(rnd.seconds){
          self ! InicioDescarga
        }
      case "entregaAlmacen" =>
        val rnd = (5 + r.nextInt(5)) / fdv
        log.debug(s"    [Tren $tren_id] random number entrega almacen $rnd")
        context.system.scheduler.scheduleOnce(rnd.seconds){
          self ! EntregarAlmacen
        }
    }
  }

  /*
  def localizacionDestinoAleatorio(localizacionOrigen: Localizacion): Localizacion = {
    var str = ""
    do {
      val r = new Random()
      val rnd = 1 + r.nextInt(5)
      rnd match {
        case 1 => str = "Madrid"
        case 2 => str = "Valencia"
        case 3 => str = "Barcelona"
        case 4 => str = "Zaragoza"
        case 5 => str = "Sevilla"
      }
    } while (localizacionOrigen.name == str)
    Localizacion(1, str)
  }
   */

  override def receive: Receive =  {
    case IniciarTren(id,capacidad,ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef) =>
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Iniciado en ${ruta.head.name} con una capacidad maxima de $capacidad paquetes, Fecha y hora: $dtEvento")
      scheduleTren = intervaloTiempoTren("recibirPaquetes",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enOrigen(id, Seq[Paquete](), capacidad, ruta.head, ruta(1), ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enOrigen(id: Int, listaPaquetesTren: Seq[Paquete], capacidad: Int, localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
    case RecibirPaquetes (listaPaquetes) =>
      scheduleTren.cancel()
      val nuevaListaPaquetesTren = listaPaquetesTren ++ listaPaquetes
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: INICIO CARGA DEL TREN, Salida de paquetes: ${nuevaListaPaquetesTren.map(p => p.id)}, Origen: ${localizacionOrigen.name}, Destino: ${localizacionDestino.name}, Fecha y hora: $dtEvento")
      scheduleTren = intervaloTiempoTren("cargarDescargarPaquetes",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enCarga(id, capacidad, nuevaListaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enCarga(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
    case FinCargaDescarga =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: FIN CARGA, Fecha y hora: $dtEvento")
      scheduleTren = intervaloTiempoTren("esperaInicioViaje",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enEsperaInicioViaje(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))

  }

  def enEsperaInicioViaje(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive = {
    case InicioViaje =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: SALIDA DESDE EL ORIGEN, Fecha y hora: $dtEvento")
      scheduleTren = intervaloTiempoTren("viaje",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enViaje(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enViaje(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive =  {
    case FinViaje =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: LLEGADA A DESTINO, Fecha y hora: $dtEvento")
      scheduleTren = intervaloTiempoTren("esperaDescargaPaquetes",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enDestinoSinDescarga(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enDestinoSinDescarga(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive =  {
    case InicioDescarga =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: INICIO DESCARGA, Fecha y hora: $dtEvento")
      scheduleTren = intervaloTiempoTren("cargarDescargarPaquetes",id, capacidad, ruta, fdv, fabMasterRef)
      context.become(enDescarga(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef))
  }

  def enDescarga(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef): Receive =  {
    case FinCargaDescarga =>
      scheduleTren.cancel()
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s"    [Tren $id] Evento: FIN DESCARGA, Fecha y hora: $dtEvento")
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

