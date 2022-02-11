package escenario1

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import escenario1.Basico.{Cliente, Localizacion}
import org.joda.time.DateTime

/**
 * @author José Antonio Antona Díaz
 */

object FabricaMaster {
  case class IniciarFabricaMaster(locsFabrica: Seq[Localizacion], fdv: Int, dtI: DateTime, dt0: DateTime, clientes: Seq[Cliente], localizaciones: Seq[Localizacion], producerRef: ActorRef)
  case class AtributosFabrica(ref: ActorRef, id: Int, localizacion: Localizacion)
  case class SalidaPaquetesMaster(capacidad: Int, ruta: Seq[Localizacion])
}

class FabricaMaster extends Actor with ActorLogging {
  import FabricaMaster._
  import Fabrica._

  override def receive: Receive = {
    case IniciarFabricaMaster(locsFabrica, fdv, dtI, dt0, clientes, localizaciones, producerRef) =>
      log.debug(s"[Fabrica Master] Iniciando con ${locsFabrica.size} fabricas")
      // Creación de los actores fábrica
      val referencias = for (i <- 1 to locsFabrica.size) yield context.actorOf(Props[Fabrica], s"fabrica_$i")
      var fabricas = Seq[AtributosFabrica]()
      for (i <- referencias.indices) {
        fabricas = fabricas :+ AtributosFabrica(referencias(i), i+1, locsFabrica(i))
        // Notificación para iniciar los actores fábrica
        fabricas(i).ref ! ResetearFabrica(fabricas(i).id, fabricas(i).localizacion, fdv, dtI, dt0, clientes, localizaciones, producerRef)
      }
      context.become(iniciado(fabricas))
  }

  def iniciado(fabricas: Seq[AtributosFabrica]): Receive = {
    case SalidaPaquetesMaster(capacidad, ruta) =>
      fabricas.foreach(f =>
        if(f.localizacion == ruta.head){
          // Reenvío del mensaje a la fábrica correspondiente
          f.ref forward SalidaPaquetes(capacidad, ruta)
        }
      )
  }

}