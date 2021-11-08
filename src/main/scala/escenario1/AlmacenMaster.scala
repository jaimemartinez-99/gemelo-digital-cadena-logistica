package escenario1

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import escenario1.Almacen.Almacen

object AlmacenMaster {
  case class IniciarAlmacenMaster(n: Int)
}

class AlmacenMaster extends Actor with ActorLogging {
  import AlmacenMaster._

  override def receive: Receive = {
    case IniciarAlmacenMaster(n) =>
      log.info(s"[AlmacenMaster] Iniciando con $n almacenes")
      val referencias = for (i <- 1 to n) yield context.actorOf(Props[Almacen], s"almacenM$i")
      log.info(s"[AlmacenMaster] $referencias")
      context.become(iniciado(referencias))
  }

  def iniciado(refs: Seq[ActorRef]): Receive = {
    case message: String => ???
  }

      }
