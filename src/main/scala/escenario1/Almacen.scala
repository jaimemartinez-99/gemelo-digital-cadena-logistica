package escenario1

import akka.actor.{Actor, ActorLogging}
import escenario1.Basico.{Localizacion, Paquete}

object Almacen {

  /**
   * Almacen
   */

  object Almacen {
    case class  ResetearAlmacen(id: Int, localizacion: Localizacion)
    case class  RecibirPaquetesAlmacen(listaPaquetes: Seq[Paquete])
  }

  class Almacen extends Actor with ActorLogging {
    import Almacen._

    override def receive: Receive = {
      case ResetearAlmacen(id, localizacion) =>
        log.info(s" [Almacen $id] Iniciado en ${localizacion.name}")
        context.become(iniciado(id, Seq[Paquete](),localizacion))
    }

    def iniciado(id: Int, listaTodosPaquetesAlmacen: Seq[Paquete], localizacion: Localizacion): Receive = {
      case RecibirPaquetesAlmacen(listaPaquetes) =>
        log.info(s" [Almacen $id] Evento: LLEGADA DE ITEMS AL ALMACEN, Han llegado los paquetes: ${listaPaquetes.map(p => p.id)}")
        val nuevaListaTodosPaquetesAlmacen = listaTodosPaquetesAlmacen ++ listaPaquetes
        log.info(s" [Almacen $id] Los paquetes que hay actualmente en el almacen son: ${nuevaListaTodosPaquetesAlmacen.map(p => p.id)}")
        context.become(iniciado(id, nuevaListaTodosPaquetesAlmacen, localizacion))
    }
  }

}
