package escenario2

import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Props}

import scala.util.Random
import scala.concurrent.duration._
import scala.util.control.Breaks.{break, breakable}

object Main2 extends App {

  // Creacion del sistema
  val system = ActorSystem("EscenarioBasicoDemo")

  // Paquete
  case class Paquete (id: Int, prioridad: Int, cliente: Cliente, localizacionDestino: Localizacion)

  // Clientes
  case class Cliente (id: Int, name: String)

  // Localizaciones
  case class Localizacion (id: Int, name: String)

  /**
   * Fabrica
   */

  import system.dispatcher //TODO ES NECESARIO ESTO??

  object Fabrica {
    case class  ResetearFabrica(localizacion: Localizacion)
    case object CrearPaquete
    case class  SalidaPaquetes (capacidadTren: Int, localizacionDestino: Localizacion)
  }

  class Fabrica extends Actor with ActorLogging {
    import Fabrica._
    import Tren._

    var schedule: Cancellable = intervaloTiempoGenerarPaquete()

    def intervaloTiempoGenerarPaquete(): Cancellable = {
      val r = new Random()
      val rnd = 1 + r.nextInt(2)
      log.info(s"[Fabrica] random number generar $rnd")
      context.system.scheduler.scheduleOnce(rnd.seconds){
        self ! CrearPaquete
      }
    }

    def take(lista: Seq[Paquete], capacidad: Int, localizacionDestino: Localizacion): Seq[Paquete] = {
      val listaVieja = lista
      var listaNueva = Seq[Paquete]()
      var i = 0
      breakable {
        for(j <- 1 to 3) {
          // Compruebo que la lista nueva no supere la capacidad y que se recorre toda la lista vieja
          while (listaNueva.size < capacidad && i < listaVieja.size) {
            if (listaVieja(i).localizacionDestino == localizacionDestino && listaVieja(i).prioridad == j) {
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
      case ResetearFabrica (localizacion) =>
        log.info(s"[Fabrica] Iniciada en ${localizacion.name}")
        context.become(iniciada(Seq[Paquete](), Seq[Paquete](),localizacion))
    }

    def iniciada(listaPaquetes: Seq[Paquete], listaTodosPaquetes: Seq[Paquete], localizacion: Localizacion): Receive = {
      case CrearPaquete =>
        val paquete_id = listaTodosPaquetes.size + 1
        val cliente = clienteAleatorio()  // Cliente aleatorio
        val localizacionDestino = localizacionDestinoAleatorio(localizacion) // Destino final aleatorio
        val prioridad = prioridadAleatoria() // Prioridad aleatoria
        val paquete = Paquete(paquete_id, prioridad, cliente, localizacionDestino)
        log.info(s"[Fabrica] Evento: ITEM GENERADO, Paquete(id: ${paquete.id}, prioridad: ${paquete.prioridad}, cliente: ${paquete.cliente.name}, destino final: ${paquete.localizacionDestino.name}) generado")
        val nuevaListaTodosPaquetes = listaTodosPaquetes :+ paquete // almacenar solo los id
        val nuevaListaPaquetes = listaPaquetes :+ paquete
        schedule.cancel()
        schedule = intervaloTiempoGenerarPaquete()
        context.become(iniciada(nuevaListaPaquetes, nuevaListaTodosPaquetes,localizacion))

      case SalidaPaquetes (capacidadTren, localizacionDestino) =>
        val listaSalidaPaquetes = take(listaPaquetes, capacidadTren, localizacionDestino)
        val listaPaquetesRestantes = listaPaquetes.diff(listaSalidaPaquetes)
        log.info(s"[Fabrica] ${listaPaquetesRestantes.map(p => p.id)} restantes")
        sender() ! RecibirPaquetes(listaSalidaPaquetes)
        context.become(iniciada(listaPaquetesRestantes, listaTodosPaquetes,localizacion))
    }
  }

  /**
   * Tren
   */

  object Tren {
    case class  IniciarTren(id: Int, capacidad: Int, localizacion: Localizacion)
    case class  RecibirPaquetes (listaPaquetes: Seq[Paquete])
    case object FinCargaDescarga
    case object InicioViaje
    case object FinViaje
    case object InicioDescarga
    case object EntregarAlmacen
  }

  class Tren extends Actor with ActorLogging {
    import Tren._
    import Fabrica._
    import Almacen._

    var scheduleRecibirPaquetes: Cancellable = _
    var scheduleCargarDescargarPaquetes: Cancellable = _
    var scheduleViaje: Cancellable = _
    var scheduleEsperaDescargaPaquetes: Cancellable = _
    var scheduleEsperaInicioViaje: Cancellable = _
    var scheduleEntregaAlmacen: Cancellable = _

    var scheduleTren: Cancellable = _

    def intervaloTiempoTren(evento: String, tren_id: Int, capacidad: Int, localizacionDestino: Localizacion): Cancellable = {
      val r = new Random()
      evento match {
        case "recibirPaquetes" =>
          val rnd = 30 + r.nextInt(10)
          log.info(s"   [Tren $tren_id] random number recibir $rnd")
          context.system.scheduler.scheduleOnce(rnd.seconds){
            fabrica ! SalidaPaquetes(capacidad, localizacionDestino)
          }

        case "cargarDescargarPaquetes" =>
          val rnd = 5 + r.nextInt(5)
          log.info(s"   [Tren $tren_id] random number cargar/descargar $rnd")
          context.system.scheduler.scheduleOnce(rnd.seconds){
            self ! FinCargaDescarga
          }
        case "viaje" =>
          val rnd = 30 + r.nextInt(10)
          log.info(s"   [Tren $tren_id] random number viaje $rnd")
          context.system.scheduler.scheduleOnce(rnd.seconds){
            self ! FinViaje
          }
        case "esperaInicioViaje" =>
          val rnd = 5 + r.nextInt(5)
          log.info(s"   [Tren $tren_id] random number espera inicio viaje $rnd")
          context.system.scheduler.scheduleOnce(rnd.seconds){
            self ! InicioViaje
          }
        case "esperaDescargaPaquetes" =>
          val rnd = 5 + r.nextInt(5)
          log.info(s"   [Tren $tren_id] random number espera descarga $rnd")
          context.system.scheduler.scheduleOnce(rnd.seconds){
            self ! InicioDescarga
          }
        case "entregaAlmacen" =>
          val rnd = 5 + r.nextInt(5)
          log.info(s"   [Tren $tren_id] random number entrega almacen $rnd")
          context.system.scheduler.scheduleOnce(rnd.seconds){
            self ! EntregarAlmacen
          }
      }
    }

    def intervaloTiempoRecibirPaquetes(tren_id: Int, capacidad: Int, localizacionDestino: Localizacion): Cancellable = {
      val r = new Random()
      val rnd = 30 + r.nextInt(10)
      log.info(s"   [Tren $tren_id] random number recibir $rnd")
      context.system.scheduler.scheduleOnce(rnd.seconds){
        fabrica ! SalidaPaquetes(capacidad, localizacionDestino)
      }
    }

    def intervaloTiempoCargarDescargarPaquetes(tren_id: Int): Cancellable = {
      val r = new Random()
      val rnd = 5 + r.nextInt(5)
      log.info(s"   [Tren $tren_id] random number cargar/descargar $rnd")
      context.system.scheduler.scheduleOnce(rnd.seconds){
        self ! FinCargaDescarga
      }
    }

    def intervaloTiempoViaje(tren_id: Int): Cancellable = {
      val r = new Random()
      val rnd = 30 + r.nextInt(10)
      log.info(s"   [Tren $tren_id] random number viaje $rnd")
      context.system.scheduler.scheduleOnce(rnd.seconds){
        self ! FinViaje
      }
    }

    def intervaloTiempoEsperaDescarga(tren_id: Int): Cancellable = {
      val r = new Random()
      val rnd = 5 + r.nextInt(5)
      log.info(s"   [Tren $tren_id] random number espera descarga $rnd")
      context.system.scheduler.scheduleOnce(rnd.seconds){
        self ! InicioDescarga
      }
    }

    def intervaloTiempoEsperaInicioViaje(tren_id: Int): Cancellable = {
      val r = new Random()
      val rnd = 5 + r.nextInt(5)
      log.info(s"   [Tren $tren_id] random number espera inicio viaje $rnd")
      context.system.scheduler.scheduleOnce(rnd.seconds){
        self ! InicioViaje
      }
    }

    def intervaloTiempoEntregaAlmacen(tren_id: Int): Cancellable = {
      val r = new Random()
      val rnd = 5 + r.nextInt(5)
      log.info(s"   [Tren $tren_id] random number entrega almacen $rnd")
      context.system.scheduler.scheduleOnce(rnd.seconds){
        self ! EntregarAlmacen
      }
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
      Localizacion(1, str)
    }

    override def receive: Receive =  {
      case IniciarTren(id,capacidad,localizacion) =>
        log.info(s"   [Tren $id] Iniciado en ${localizacion.name} con una capacidad maxima de $capacidad paquetes")
        val localizacionDestino = localizacionDestinoAleatorio(localizacion)
        scheduleTren = intervaloTiempoTren("recibirPaquetes",id, capacidad, localizacionDestino)
        context.become(enOrigen(id, capacidad, localizacion, localizacionDestino))
    }

    def enOrigen(id: Int, capacidad: Int, localizacionOrigen: Localizacion, localizacionDestino: Localizacion): Receive = {
      case RecibirPaquetes (listaPaquetes) =>
        scheduleTren.cancel()
        log.info(s"   [Tren $id] Evento: INICIO CARGA DEL TREN, Salida de paquetes: ${listaPaquetes.map(p => p.id)}, Origen: ${localizacionOrigen.name}, Destino: ${localizacionDestino.name}")
        scheduleTren = intervaloTiempoTren("cargarDescargarPaquetes",id, capacidad, localizacionDestino)
        context.become(enCarga(id, capacidad, listaPaquetes, localizacionOrigen, localizacionDestino))
    }

    def enCarga(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion): Receive = {
      case FinCargaDescarga =>
        scheduleTren.cancel()
        log.info(s"   [Tren $id] Evento: FIN CARGA")
        scheduleTren = intervaloTiempoTren("esperaInicioViaje",id, capacidad, localizacionDestino)
        context.become(enEsperaInicioViaje(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino))

    }

    def enEsperaInicioViaje(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion): Receive = {
      case InicioViaje =>
        scheduleTren.cancel()
        log.info(s"   [Tren $id] Evento: SALIDA DESDE EL ORIGEN")
        scheduleTren = intervaloTiempoTren("viaje",id, capacidad, localizacionDestino)
        context.become(enViaje(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino))
    }

    def enViaje(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion): Receive =  {
      case FinViaje =>
        scheduleTren.cancel()
        log.info(s"   [Tren $id] Evento: LLEGADA A DESTINO")
        scheduleTren = intervaloTiempoTren("esperaDescargaPaquetes",id, capacidad, localizacionDestino)
        context.become(enDestinoSinDescarga(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino))
    }

    def enDestinoSinDescarga(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion): Receive =  {
      case InicioDescarga =>
        scheduleTren.cancel()
        log.info(s"   [Tren $id] Evento: INICIO DESCARGA")
        scheduleTren = intervaloTiempoTren("cargarDescargarPaquetes",id, capacidad, localizacionDestino)
        context.become(enDescarga(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino))
    }

    def enDescarga(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion): Receive =  {
      case FinCargaDescarga =>
        scheduleTren.cancel()
        log.info(s"   [Tren $id] Evento: FIN DESCARGA")
        scheduleTren = intervaloTiempoTren("entregaAlmacen",id, capacidad, localizacionDestino)
        context.become(enDestino(id, capacidad, listaPaquetesTren, localizacionOrigen, localizacionDestino))
    }

    def enDestino(id: Int, capacidad: Int, listaPaquetesTren: Seq[Paquete], localizacionOrigen: Localizacion, localizacionDestino: Localizacion): Receive = {
      case EntregarAlmacen =>
        scheduleTren.cancel()
        almacen ! RecibirPaquetesAlmacen(listaPaquetesTren)
      // context.become(enOrigen())
    }
  }

  /**
   * Almacen
   */

  object Almacen {
    case class  ResetearAlmacen(localizacion: Localizacion)
    case class  RecibirPaquetesAlmacen(listaPaquetes: Seq[Paquete])
  }

  class Almacen extends Actor with ActorLogging {
    import Almacen._

    override def receive: Receive = {
      case ResetearAlmacen(localizacion) =>
        log.info(s"[Almacen] Iniciado en ${localizacion.name}")
        context.become(iniciado(Seq[Paquete](),localizacion))
    }

    def iniciado(listaTodosPaquetesAlmacen: Seq[Paquete], localizacion: Localizacion): Receive = {
      case RecibirPaquetesAlmacen(listaPaquetes) =>
        log.info(s"[Almacen] Evento: LLEGADA DE ITEMS AL ALMACEN, Han llegado los paquetes: ${listaPaquetes.map(p => p.id)}")
        val nuevaListaTodosPaquetesAlmacen = listaTodosPaquetesAlmacen ++ listaPaquetes
        log.info(s"[Almacen] Los paquetes que hay actualmente en el almacen son: ${nuevaListaTodosPaquetesAlmacen.map(p => p.id)}")
        context.become(iniciado(nuevaListaTodosPaquetesAlmacen, localizacion))
    }
  }

  // Creacion de los actores principales
  val fabrica = system.actorOf(Props[Fabrica], "fabrica")
  val tren = system.actorOf(Props[Tren], "tren")
  val almacen = system.actorOf(Props[Almacen], "almacen")

  import Tren._
  import Almacen._
  import Fabrica._

  // Inicializacion de los actores principales
  tren ! IniciarTren(1,6,Localizacion(1,"Madrid"))
  almacen ! ResetearAlmacen(Localizacion(1,"Valencia"))
  fabrica ! ResetearFabrica(Localizacion(1,"Madrid"))

}
