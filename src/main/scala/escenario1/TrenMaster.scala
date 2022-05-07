package escenario1

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import escenario1.Basico.{Estacion, Localizacion}
import org.joda.time.DateTime

/**
 * @author José Antonio Antona Díaz
 */

object TrenMaster {
  case class IniciarTrenMaster(estaciones: Seq[Estacion], rutas: Seq[Seq[Localizacion]], capacidades: Seq[Int], fdv: Int, dtI: DateTime, dt0: DateTime, fabMasterRef: ActorRef, almMasterRef: ActorRef, producerRef: ActorRef)
  case class AtributosTrenes(ref: ActorRef, id: Int, capacidad: Int, ruta: Seq[Localizacion])
}

class TrenMaster extends Actor with ActorLogging {
  import TrenMaster._
  import Tren._

  override def receive: Receive = {
    case IniciarTrenMaster(estaciones, rutas, capacidades, fdv, dtI, dt0, fabMasterRef, almMasterRef, producerRef) =>
      log.debug(s"[TrenMaster] Iniciando con ${rutas.size} trenes")
      // Creación de los actores tren
      val referencias = for (i <- 1 to rutas.size) yield context.actorOf(Props[Tren], s"tren_$i")
      var trenes = Seq[AtributosTrenes]()
      for (i <- referencias.indices) {
        trenes = trenes :+ AtributosTrenes(referencias(i), i+1, capacidades(i), rutas(i))
        // Notificación para iniciar los actores tren
        trenes(i).ref ! IniciarTren(trenes(i).id, trenes(i).capacidad, estaciones, trenes(i).ruta, fdv, dtI, dt0, fabMasterRef, almMasterRef, producerRef)
      }
  }
}
