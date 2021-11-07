package escenario1

import akka.actor.{Actor, ActorLogging, Cancellable}
import escenario1.Almacen.Almacen.RecibirPaquetesAlmacen
import escenario1.Basico.{Localizacion, Paquete}
import escenario1.Fabrica.Fabrica.SalidaPaquetes
import escenario1.App.{almacen, fabrica1, fabrica2, fabrica3, fabrica4, fabrica5, system}

import scala.concurrent.duration._
import scala.util.Random

object Tren {

  /**
   * Tren
   */

  import system.dispatcher //TODO ES NECESARIO ESTO??

  object Tren {
    case class  IniciarTren(id: Int, capacidad: Int, ruta: Seq[Localizacion])
    case class  RecibirPaquetes (listaPaquetes: Seq[Paquete])
    case object FinCargaDescarga
    case object InicioViaje
    case object FinViaje
    case object InicioDescarga
    case object EntregarAlmacen
  }

  class Tren extends Actor with ActorLogging {
    import Tren._

    var scheduleTren: Cancellable = _

    def intervaloTiempoTren(evento: String, tren_id: Int, capacidad: Int, localizacionOrigen: Localizacion, localizacionDestino: Localizacion): Cancellable = {
      val r = new Random()
      evento match {
        case "recibirPaquetes" =>
          val rnd = 30 + r.nextInt(10)
          log.info(s"    [Tren $tren_id] random number recibir $rnd")
          context.system.scheduler.scheduleOnce(rnd.seconds){
            localizacionOrigen.name match {
              case "Madrid" =>  fabrica1 ! SalidaPaquetes(capacidad, localizacionDestino)
              case "Zaragoza" => fabrica2 ! SalidaPaquetes(capacidad, localizacionDestino)
              case "Valencia" => fabrica3 ! SalidaPaquetes(capacidad, localizacionDestino)
              case "Barcelona" => fabrica4 ! SalidaPaquetes(capacidad, localizacionDestino)
              case "Sevilla" => fabrica5 ! SalidaPaquetes(capacidad, localizacionDestino)
            }
          }

        case "cargarDescargarPaquetes" =>
          val rnd = 5 + r.nextInt(5)
          log.info(s"    [Tren $tren_id] random number cargar/descargar $rnd")
          context.system.scheduler.scheduleOnce(rnd.seconds){
            self ! FinCargaDescarga
          }
        case "viaje" =>
          val rnd = 30 + r.nextInt(10)
          log.info(s"    [Tren $tren_id] random number viaje $rnd")
          context.system.scheduler.scheduleOnce(rnd.seconds){
            self ! FinViaje
          }
        case "esperaInicioViaje" =>
          val rnd = 5 + r.nextInt(5)
          log.info(s"    [Tren $tren_id] random number espera inicio viaje $rnd")
          context.system.scheduler.scheduleOnce(rnd.seconds){
            self ! InicioViaje
          }
        case "esperaDescargaPaquetes" =>
          val rnd = 5 + r.nextInt(5)
          log.info(s"    [Tren $tren_id] random number espera descarga $rnd")
          context.system.scheduler.scheduleOnce(rnd.seconds){
            self ! InicioDescarga
          }
        case "entregaAlmacen" =>
          val rnd = 5 + r.nextInt(5)
          log.info(s"    [Tren $tren_id] random number entrega almacen $rnd")
          context.system.scheduler.scheduleOnce(rnd.seconds){
            self ! EntregarAlmacen
          }
      }
    }

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

    override def receive: Receive =  {
      case IniciarTren(id,capacidad,ruta) =>
        log.info(s"    [Tren $id] Iniciado en ${ruta.head.name} con una capacidad maxima de $capacidad paquetes")
        scheduleTren = intervaloTiempoTren("recibirPaquetes",id, capacidad, ruta.head, ruta(1))
        context.become(enOrigen(id, capacidad, ruta.head, ruta(1), ruta))
    }

    def enOrigen(id: Int, capacidad: Int, localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion]): Receive = {
      case RecibirPaquetes (listaPaquetes) =>
        scheduleTren.cancel()
        log.info(s"    [Tren $id] Evento: INICIO CARGA DEL TREN, Salida de paquetes: ${listaPaquetes.map(p => p.id)}, Origen: ${localizacionOrigen.name}, Destino: ${localizacionDestino.name}")
        scheduleTren = intervaloTiempoTren("cargarDescargarPaquetes",id, capacidad, localizacionOrigen, localizacionDestino)
        context.become(enCarga(id, capacidad, listaPaquetes, localizacionOrigen, localizacionDestino, ruta))
    }

    def enCarga(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion]): Receive = {
      case FinCargaDescarga =>
        scheduleTren.cancel()
        log.info(s"    [Tren $id] Evento: FIN CARGA")
        scheduleTren = intervaloTiempoTren("esperaInicioViaje",id, capacidad, localizacionOrigen, localizacionDestino)
        context.become(enEsperaInicioViaje(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta))

    }

    def enEsperaInicioViaje(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion]): Receive = {
      case InicioViaje =>
        scheduleTren.cancel()
        log.info(s"    [Tren $id] Evento: SALIDA DESDE EL ORIGEN")
        scheduleTren = intervaloTiempoTren("viaje",id, capacidad, localizacionOrigen, localizacionDestino)
        context.become(enViaje(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta))
    }

    def enViaje(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion]): Receive =  {
      case FinViaje =>
        scheduleTren.cancel()
        log.info(s"    [Tren $id] Evento: LLEGADA A DESTINO")
        scheduleTren = intervaloTiempoTren("esperaDescargaPaquetes",id, capacidad, localizacionOrigen, localizacionDestino)
        context.become(enDestinoSinDescarga(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta))
    }

    def enDestinoSinDescarga(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion]): Receive =  {
      case InicioDescarga =>
        scheduleTren.cancel()
        log.info(s"    [Tren $id] Evento: INICIO DESCARGA")
        scheduleTren = intervaloTiempoTren("cargarDescargarPaquetes",id, capacidad, localizacionOrigen, localizacionDestino)
        context.become(enDescarga(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta))
    }

    def enDescarga(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion]): Receive =  {
      case FinCargaDescarga =>
        scheduleTren.cancel()
        log.info(s"    [Tren $id] Evento: FIN DESCARGA")
        scheduleTren = intervaloTiempoTren("entregaAlmacen",id, capacidad, localizacionOrigen, localizacionDestino)
        context.become(enDestino(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino, ruta))
    }

    def enDestino(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion, ruta: Seq[Localizacion]): Receive = {
      case EntregarAlmacen =>
        scheduleTren.cancel()
        localizacionDestino.name match {
          case "Zaragoza" => almacen ! RecibirPaquetesAlmacen(listaPaquetesTren)
          case "Madrid" => almacen ! RecibirPaquetesAlmacen(listaPaquetesTren)
          case "Valencia" => almacen ! RecibirPaquetesAlmacen(listaPaquetesTren)
          case "Barcelona" => almacen ! RecibirPaquetesAlmacen(listaPaquetesTren)
          case "Sevilla" => almacen ! RecibirPaquetesAlmacen(listaPaquetesTren)
        }
        val nuevaRuta: Seq[Localizacion] = ruta.tail :+ ruta.head
        log.info(s"    [Tren $id] En ${nuevaRuta.head.name} con una capacidad maxima de $capacidad paquetes")
        scheduleTren = intervaloTiempoTren("recibirPaquetes", id, capacidad, nuevaRuta.head, nuevaRuta(1))
        context.become(enOrigen(id, capacidad, nuevaRuta.head, nuevaRuta(1), nuevaRuta))
    }
  }

}
