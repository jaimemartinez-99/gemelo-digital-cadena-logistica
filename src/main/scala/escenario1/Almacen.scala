package escenario1

import akka.actor.{Actor, ActorLogging}
import com.github.nscala_time.time.Imports.{richReadableInstant, richReadableInterval}
import escenario1.Basico.{Localizacion, Paquete}
import org.joda.time.DateTime

/**
 * Almacen
 */

object Almacen {
  case class  ResetearAlmacen(id: Int, localizacion: Localizacion, fdv: Int, dtI: DateTime, dt0: DateTime)
  case class  RecibirPaquetesAlmacen(listaPaquetes: Seq[Paquete])
}

class Almacen extends Actor with ActorLogging {
  import Almacen._

  override def receive: Receive = {
    case ResetearAlmacen(id, localizacion,fdv, dtI, dt0) =>
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s" [Almacen $id] Iniciado en ${localizacion.name}, Fecha y hora: $dtEvento")
      context.become(iniciado(id, Seq[Paquete](),localizacion, fdv, dtI, dt0))
  }

  def iniciado(id: Int, listaTodosPaquetesAlmacen: Seq[Paquete], localizacion: Localizacion, fdv: Int, dtI: DateTime, dt0: DateTime): Receive = {
    case RecibirPaquetesAlmacen(listaPaquetes) =>
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis * fdv)
      log.debug(s" [Almacen $id] Evento: LLEGADA DE ITEMS AL ALMACEN, Han llegado los paquetes: ${listaPaquetes.map(p => p.id)}, Fecha y hora: $dtEvento")
      val nuevaListaTodosPaquetesAlmacen = listaTodosPaquetesAlmacen ++ listaPaquetes
      log.debug(s" [Almacen $id] Los paquetes que hay actualmente en el almacen son: ${nuevaListaTodosPaquetesAlmacen.map(p => p.id)}")
      context.become(iniciado(id, nuevaListaTodosPaquetesAlmacen, localizacion, fdv, dtI, dt0))
  }
}
