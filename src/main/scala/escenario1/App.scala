package escenario1

import akka.actor.{ActorSystem, Props}
import escenario1.Almacen.Almacen
import escenario1.Basico.Localizacion
import escenario1.Fabrica.Fabrica
import escenario1.Tren.Tren

object App extends App {

  // Creacion del sistema
  val system = ActorSystem("EscenarioBasicoDemo")

  // Creacion de los actores principales
  val fabrica1 = system.actorOf(Props[Fabrica], "fabrica1")
  val fabrica2 = system.actorOf(Props[Fabrica], "fabrica2")
  val fabrica3 = system.actorOf(Props[Fabrica], "fabrica3")
  val fabrica4 = system.actorOf(Props[Fabrica], "fabrica4")
  val fabrica5 = system.actorOf(Props[Fabrica], "fabrica5")

  val tren = system.actorOf(Props[Tren], "tren")
  val almacen = system.actorOf(Props[Almacen], "almacen")

  import Tren._
  import Almacen._
  import Fabrica._

  // Inicializacion de los actores principales
  fabrica1 ! ResetearFabrica(1, Localizacion(1,"Madrid"))
  fabrica2 ! ResetearFabrica(2, Localizacion(1,"Zaragoza"))
  fabrica3 ! ResetearFabrica(3, Localizacion(1,"Valencia"))
  fabrica4 ! ResetearFabrica(4, Localizacion(1,"Barcelona"))
  fabrica5 ! ResetearFabrica(5, Localizacion(1,"Sevilla"))

  val locMadrid = Localizacion(1,"Madrid")
  val locZaragoza = Localizacion(1,"Zaragoza")
  val locValencia = Localizacion(1,"Valencia")
  val locBarcelona = Localizacion(1,"Barcelona")
  val locSevilla = Localizacion(1,"Sevilla")
  var ruta1 = Seq[Localizacion](locMadrid, locZaragoza)

  tren ! IniciarTren(1,6,ruta1)
  almacen ! ResetearAlmacen(Localizacion(1,"Valencia"))


}
