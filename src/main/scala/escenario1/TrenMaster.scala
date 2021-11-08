package escenario1

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import escenario1.Tren.Tren

object TrenMaster {
  case class IniciarTrenMaster(n: Int)
}

class TrenMaster extends Actor with ActorLogging {
  import TrenMaster._

  override def receive: Receive = {
    case IniciarTrenMaster(n) =>
      log.info(s"[TrenMaster] Iniciando con $n trenes")
      val referencias = for (i <- 1 to n) yield context.actorOf(Props[Tren], s"trenM$i")
      log.info(s"[TrenMaster] $referencias")
      context.become(iniciado(referencias))
  }

  def iniciado(refs: Seq[ActorRef]): Receive = {
    case message: String => ???
  }

}
