package escenario1

import akka.actor.{Actor, ActorLogging, Cancellable}
import com.github.nscala_time.time.Imports.{richReadableInstant, richReadableInterval}
import escenario1.Basico.{Cliente, Localizacion, Paquete}
import escenario1.App.system
import org.joda.time.DateTime

import scala.util.Random
import scala.concurrent.duration._
import scala.util.control.Breaks.{break, breakable}

/**
 * Fabrica
 */

object Fabrica {
  case class  ResetearFabrica(id: Int, localizacion: Localizacion, fdv: Int, dtI: DateTime, dt0: DateTime)
  case object CrearPaquete
  case class  SalidaPaquetes (capacidadTren: Int, ruta: Seq[Localizacion])
}

class Fabrica extends Actor with ActorLogging {
  import Fabrica._
  import Tren._
  import system.dispatcher

  var schedule: Cancellable = _

  def intervaloTiempoGenerarPaquete(id: Int, fdv: Int): Cancellable = {
    val r = new Random()
    val rnd = (6 + r.nextInt(2) ) / fdv
    log.debug(s"[Fabrica $id] random number generar $rnd")
    context.system.scheduler.scheduleOnce(rnd.seconds){
      self ! CrearPaquete
    }
  }

  def take(lista: Seq[Paquete], capacidad: Int, ruta: Seq[Localizacion]): Seq[Paquete] = {
    val listaVieja = lista
    var listaNueva = Seq[Paquete]()
    var i = 0
    breakable {
      for(j <- 1 to 3) {
        for(locDestino <- ruta.tail){
          // Compruebo que la lista nueva no supere la capacidad y que se recorre toda la lista vieja
          while (listaNueva.size < capacidad && i < listaVieja.size) {
            if (listaVieja(i).localizacionDestino == locDestino && listaVieja(i).prioridad == j) {
              listaNueva = listaNueva :+ listaVieja(i)
            }
            if (listaNueva.size == capacidad){
              break
            }
            i += 1
          }
          i = 0
        }
      }
    }
    listaNueva
  }

  def clienteAleatorio(): Cliente = {
    val r = new Random()
    val rnd = 1 + r.nextInt(5)
    Cliente(rnd,s"CLIENTE $rnd")
  }

  def prioridadAleatoria(): Int = {
    val r = new Random()
    val rnd = 1 + r.nextInt(3)
    rnd
  }

  def localizacionDestinoAleatorio(localizacionOrigen: Localizacion): Localizacion = {
    var str = ""
    do {
      val r = new Random()
      val rnd = 1 + r.nextInt(5)
      rnd match {
        case 1 => str = "Madrid"
        case 2 => str = "Valencia"
        case 3 => str = "Barcelona"
        case 4 => str = "Zaragoza"
        case 5 => str = "Sevilla"
      }
    } while (localizacionOrigen.name == str)
    Localizacion(1,str)
  }

  override def receive: Receive = {
    case ResetearFabrica (id, localizacion, fdv, dtI, dt0) =>
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis)
      log.debug(s"[Fabrica $id] Iniciada en ${localizacion.name}, Fecha y hora: $dtEvento")
      schedule = intervaloTiempoGenerarPaquete(id, fdv)
      context.become(iniciada(id, Seq[Paquete](), Seq[Int](), localizacion, fdv, dtI, dt0))
  }

  def iniciada(id: Int, listaPaquetes: Seq[Paquete], listaTodosIdPaquetes: Seq[Int], localizacion: Localizacion, fdv: Int, dtI: DateTime, dt0: DateTime): Receive = {
    case CrearPaquete =>
      val paquete_id = listaTodosIdPaquetes.size + 1
      val cliente = clienteAleatorio()  // Cliente aleatorio
      val localizacionDestino = localizacionDestinoAleatorio(localizacion) // Destino final aleatorio
      val prioridad = prioridadAleatoria() // Prioridad aleatoria
      val paquete = Paquete(paquete_id, prioridad, cliente, localizacionDestino)
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis)
      log.debug(s"[Fabrica $id] Evento: ITEM GENERADO, Paquete(id: ${paquete.id}, prioridad: ${paquete.prioridad}, cliente: ${paquete.cliente.name}, destino final: ${paquete.localizacionDestino.name}) generado, Fecha y hora: $dtEvento")
      val nuevaListaTodosIdPaquetes = listaTodosIdPaquetes :+ paquete.id
      val nuevaListaPaquetes = listaPaquetes :+ paquete
      schedule.cancel()
      schedule = intervaloTiempoGenerarPaquete(id, fdv)
      context.become(iniciada(id, nuevaListaPaquetes, nuevaListaTodosIdPaquetes,localizacion, fdv, dtI, dt0))

    case SalidaPaquetes (capacidadTren, ruta) =>
      val listaSalidaPaquetes = take(listaPaquetes, capacidadTren, ruta)
      val listaPaquetesRestantes = listaPaquetes.diff(listaSalidaPaquetes)
      val dtEvento = dtI.plus((dt0 to DateTime.now).millis)
      log.debug(s"[Fabrica $id] ${listaPaquetesRestantes.map(p => p.id)} restantes, Fecha y hora: $dtEvento")
      sender() ! RecibirPaquetes(listaSalidaPaquetes)
      context.become(iniciada(id, listaPaquetesRestantes, listaTodosIdPaquetes,localizacion, fdv, dtI, dt0))
  }
}
