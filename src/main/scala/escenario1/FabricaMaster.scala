package escenario1

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import escenario1.Basico.Localizacion
import org.joda.time.DateTime


object FabricaMaster {
  case class IniciarFabricaMaster(localizaciones: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime)
  case class AtributosFabrica(ref: ActorRef, id: Int, localizacion: Localizacion)
  case class SalidaPaquetesMaster(capacidad: Int, ruta: Seq[Localizacion])
}

class FabricaMaster extends Actor with ActorLogging {
  import FabricaMaster._
  import Fabrica._

  override def receive: Receive = {
    case IniciarFabricaMaster(localizaciones, fdv, dtI, dt0) =>
      log.debug(s"[Fabrica Master] Iniciando con ${localizaciones.size} fabricas")
      val referencias = for (i <- 1 to localizaciones.size) yield context.actorOf(Props[Fabrica], s"fabrica_$i")
      var fabricas = Seq[AtributosFabrica]()
      for (i <- referencias.indices) {
        fabricas = fabricas :+ AtributosFabrica(referencias(i), i+1+10, localizaciones(i))
        fabricas(i).ref ! ResetearFabrica(fabricas(i).id, fabricas(i).localizacion, fdv, dtI, dt0)
      }
      context.become(iniciado(fabricas))
  }

  def iniciado(fabricas: Seq[AtributosFabrica]): Receive = {
    case SalidaPaquetesMaster(capacidad, ruta) =>
      fabricas.foreach(f =>
        if(f.localizacion == ruta.head){
          f.ref forward SalidaPaquetes(capacidad, ruta)
        }
      )
  }

}