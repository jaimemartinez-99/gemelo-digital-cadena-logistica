package escenario1

import akka.actor.{Actor, ActorLogging, Cancellable}
import escenario1.Basico.{Cliente, Localizacion, Paquete}
import escenario1.App.system
import escenario1.Tren.Tren.RecibirPaquetes

import scala.util.Random
import scala.concurrent.duration._
import scala.util.control.Breaks.{break, breakable}

object Fabrica {

  /**
   * Fabrica
   */

  import system.dispatcher //TODO ES NECESARIO ESTO??

  object Fabrica {
    case class  ResetearFabrica(id: Int, localizacion: Localizacion)
    case object CrearPaquete
    case class  SalidaPaquetes (capacidadTren: Int, ruta: Seq[Localizacion])
  }

  class Fabrica extends Actor with ActorLogging {
    import Fabrica._

    var schedule: Cancellable = _

    def intervaloTiempoGenerarPaquete(id: Int): Cancellable = {
      val r = new Random()
      val rnd = 2 + r.nextInt(2)
      // log.info(s"[Fabrica $id] random number generar $rnd")
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
      case ResetearFabrica (id, localizacion) =>
        log.info(s"[Fabrica $id] Iniciada en ${localizacion.name}")
        schedule = intervaloTiempoGenerarPaquete(id)
        context.become(iniciada(id, Seq[Paquete](), Seq[Int](), localizacion))
    }

    def iniciada(id: Int, listaPaquetes: Seq[Paquete], listaTodosIdPaquetes: Seq[Int], localizacion: Localizacion): Receive = {
      case CrearPaquete =>
        val paquete_id = listaTodosIdPaquetes.size + 1
        val cliente = clienteAleatorio()  // Cliente aleatorio
        val localizacionDestino = localizacionDestinoAleatorio(localizacion) // Destino final aleatorio
        val prioridad = prioridadAleatoria() // Prioridad aleatoria
        val paquete = Paquete(paquete_id, prioridad, cliente, localizacionDestino)
        log.info(s"[Fabrica $id] Evento: ITEM GENERADO, Paquete(id: ${paquete.id}, prioridad: ${paquete.prioridad}, cliente: ${paquete.cliente.name}, destino final: ${paquete.localizacionDestino.name}) generado")
        val nuevaListaTodosIdPaquetes = listaTodosIdPaquetes :+ paquete.id
        val nuevaListaPaquetes = listaPaquetes :+ paquete
        schedule.cancel()
        schedule = intervaloTiempoGenerarPaquete(id)
        context.become(iniciada(id, nuevaListaPaquetes, nuevaListaTodosIdPaquetes,localizacion))

      case SalidaPaquetes (capacidadTren, ruta) =>
        val listaSalidaPaquetes = take(listaPaquetes, capacidadTren, ruta)
        val listaPaquetesRestantes = listaPaquetes.diff(listaSalidaPaquetes)
        log.info(s"[Fabrica $id] ${listaPaquetesRestantes.map(p => p.id)} restantes")
        sender() ! RecibirPaquetes(listaSalidaPaquetes)
        context.become(iniciada(id, listaPaquetesRestantes, listaTodosIdPaquetes,localizacion))
    }
  }

}
