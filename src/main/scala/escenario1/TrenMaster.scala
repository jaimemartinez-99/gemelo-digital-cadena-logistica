package escenario1

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import escenario1.Basico.Localizacion
import org.joda.time.DateTime

object TrenMaster {
  case class IniciarTrenMaster(rutas: Seq[Seq[Localizacion]], capacidades: Seq[Int], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef, producerRef: ActorRef)
  case class AtributosTrenes(ref: ActorRef, id: Int, capacidad: Int, ruta: Seq[Localizacion])
}

class TrenMaster extends Actor with ActorLogging {
  import TrenMaster._
  import Tren._

  override def receive: Receive = {
    case IniciarTrenMaster(rutas, capacidades, fdv, dtI, dt0, fabMasterRef, almMasterRef, producerRef) =>
      log.debug(s"[TrenMaster] Iniciando con ${rutas.size} trenes")
      val referencias = for (i <- 1 to rutas.size) yield context.actorOf(Props[Tren], s"tren_$i")
      var trenes = Seq[AtributosTrenes]()
      for (i <- referencias.indices) {
        trenes = trenes :+ AtributosTrenes(referencias(i), i+1+10, capacidades(i), rutas(i))
        trenes(i).ref ! IniciarTren(trenes(i).id, trenes(i).capacidad, trenes(i).ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef, producerRef)
      }
  }
}
