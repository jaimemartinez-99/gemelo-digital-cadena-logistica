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

  val fabricaMaster = system.actorOf(Props[FabricaMaster], "fabricaMaster")

  val tren1 = system.actorOf(Props[Tren], "tren1")
  val tren2 = system.actorOf(Props[Tren], "tren2")

  val trenMaster = system.actorOf(Props[TrenMaster], "trenMaster")

  val almacen1 = system.actorOf(Props[Almacen], "almacen1")
  val almacen2 = system.actorOf(Props[Almacen], "almacen2")
  val almacen3 = system.actorOf(Props[Almacen], "almacen3")
  val almacen4 = system.actorOf(Props[Almacen], "almacen4")
  val almacen5 = system.actorOf(Props[Almacen], "almacen5")

  val almacenMaster = system.actorOf(Props[AlmacenMaster], "almacenMaster")

  import FabricaMaster._
  import Fabrica._
  import TrenMaster._
  import Tren._
  import AlmacenMaster._
  import Almacen._

  // Inicializacion de los actores principales
  val locMadrid = Localizacion(1,"Madrid")
  val locZaragoza = Localizacion(1,"Zaragoza")
  val locValencia = Localizacion(1,"Valencia")
  val locBarcelona = Localizacion(1,"Barcelona")
  val locSevilla = Localizacion(1,"Sevilla")

  //fabrica1 ! ResetearFabrica(1, locMadrid)
  //fabrica2 ! ResetearFabrica(2, locZaragoza)
  // fabrica3 ! ResetearFabrica(3, locValencia)
  //fabrica4 ! ResetearFabrica(4, locBarcelona)
  // fabrica5 ! ResetearFabrica(5, locSevilla)

  val ruta1 = Seq[Localizacion](locMadrid, locZaragoza, locBarcelona)
  val ruta2 = Seq[Localizacion](locValencia, locMadrid, locSevilla)

  tren1 ! IniciarTren(1, 10, ruta1)
  // tren2 ! IniciarTren(2,8,ruta2)

  almacen1 ! ResetearAlmacen(1, locMadrid)
  almacen2 ! ResetearAlmacen(2, locZaragoza)
  // almacen3 ! ResetearAlmacen(3, locValencia)
  almacen4 ! ResetearAlmacen(4, locBarcelona)
  // almacen5 ! ResetearAlmacen(5, locSevilla)

  val locsFabrica = Seq[Localizacion](locMadrid, locZaragoza, locValencia, locBarcelona, locSevilla)
  val locsAlmacen = Seq[Localizacion](locMadrid, locZaragoza, locValencia, locBarcelona, locSevilla)
  fabricaMaster ! IniciarFabricaMaster(locsFabrica)
  trenMaster ! IniciarTrenMaster(2)
  almacenMaster ! IniciarAlmacenMaster(locsAlmacen)
}
