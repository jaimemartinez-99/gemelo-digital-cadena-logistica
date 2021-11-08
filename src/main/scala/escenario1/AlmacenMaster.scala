package escenario1

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import escenario1.Almacen.Almacen
import escenario1.Basico.{Localizacion, Paquete}

object AlmacenMaster {
  case class IniciarAlmacenMaster(localizaciones: Seq[Localizacion])
  case class AtributosAlmacen (ref: ActorRef, id: Int, localizacion: Localizacion)
  case class RecibirPaquetesAlmacenMaster(listaPaquetes: Seq[Paquete], locDestino: Localizacion)
}

class AlmacenMaster extends Actor with ActorLogging {
  import AlmacenMaster._
  import Almacen._

  override def receive: Receive = {
    case IniciarAlmacenMaster(localizaciones) =>
      log.info(s"[AlmacenMaster] Iniciando con ${localizaciones.size} almacenes")
      val referencias = for (i <- 1 to localizaciones.size) yield context.actorOf(Props[Almacen], s"almacen_$i")
      var almacenes = Seq[AtributosAlmacen]()
      for (i <- referencias.indices) {
        almacenes = almacenes :+ AtributosAlmacen(referencias(i), i+1+10, localizaciones(i))
        almacenes(i).ref ! ResetearAlmacen(almacenes(i).id, almacenes(i).localizacion)
      }
      log.info(s"$almacenes")
      context.become(iniciado(almacenes))
  }

  def iniciado(almacenes: Seq[AtributosAlmacen]): Receive = {
    case RecibirPaquetesAlmacenMaster(listaPaquetes, locDestino) =>
      almacenes.foreach(a =>
        if(a.localizacion == locDestino) {
          a.ref forward RecibirPaquetesAlmacen(listaPaquetes)
        }
      )
  }

}
