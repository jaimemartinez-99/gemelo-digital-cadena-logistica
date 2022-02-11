package escenario1

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import escenario1.Basico.{Localizacion, Paquete}
import org.joda.time.DateTime

/**
 * @author José Antonio Antona Díaz
 */

object AlmacenMaster {
  case class IniciarAlmacenMaster(localizaciones: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime)
  case class AtributosAlmacen (ref: ActorRef, id: Int, localizacion: Localizacion)
  case class RecibirPaquetesAlmacenMaster(listaPaquetes: Seq[Paquete], locDestino: Localizacion)
}

class AlmacenMaster extends Actor with ActorLogging {
  import AlmacenMaster._
  import Almacen._

  override def receive: Receive = {
    case IniciarAlmacenMaster(localizaciones, fdv, dtI, dt0) =>
      log.debug(s"[AlmacenMaster] Iniciando con ${localizaciones.size} almacenes")
      // Creación de los actores almacén
      val referencias = for (i <- 1 to localizaciones.size) yield context.actorOf(Props[Almacen], s"almacen_$i")
      var almacenes = Seq[AtributosAlmacen]()
      for (i <- referencias.indices) {
        almacenes = almacenes :+ AtributosAlmacen(referencias(i), i+1, localizaciones(i))
        // Notificación para iniciar los actores almacén
        almacenes(i).ref ! ResetearAlmacen(almacenes(i).id, almacenes(i).localizacion, fdv, dtI, dt0)
      }
      context.become(iniciado(almacenes))
  }

  def iniciado(almacenes: Seq[AtributosAlmacen]): Receive = {
    case RecibirPaquetesAlmacenMaster(listaPaquetes, locDestino) =>
      almacenes.foreach(a =>
        if(a.localizacion == locDestino) {
          // Reenvío del mensaje al almacén correspondiente
          a.ref forward RecibirPaquetesAlmacen(listaPaquetes)
        }
      )
  }

}
