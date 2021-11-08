package escenario1

import akka.actor.{ActorSystem, Props}
import escenario1.Basico.Localizacion
import escenario1.Tren.Tren

object App extends App {

  // Creacion del sistema
  val system = ActorSystem("EscenarioBasicoDemo")

  // Creacion de los actores principales
  val tren1 = system.actorOf(Props[Tren], "tren1")
  val tren2 = system.actorOf(Props[Tren], "tren2")

  val fabricaMaster = system.actorOf(Props[FabricaMaster], "fabricaMaster")
  val trenMaster = system.actorOf(Props[TrenMaster], "trenMaster")
  val almacenMaster = system.actorOf(Props[AlmacenMaster], "almacenMaster")

  import FabricaMaster._
  import TrenMaster._
  import Tren._
  import AlmacenMaster._

  // Inicializacion de las localizaciones, rutas y actores principales
  val locMadrid = Localizacion(1,"Madrid")
  val locZaragoza = Localizacion(1,"Zaragoza")
  val locValencia = Localizacion(1,"Valencia")
  val locBarcelona = Localizacion(1,"Barcelona")
  val locSevilla = Localizacion(1,"Sevilla")

  val ruta1 = Seq[Localizacion](locMadrid, locZaragoza, locBarcelona)
  val ruta2 = Seq[Localizacion](locValencia, locMadrid, locSevilla)

  // tren1 ! IniciarTren(1, 10, ruta1)
  // tren2 ! IniciarTren(2,8,ruta2)

  val locsFabrica = Seq[Localizacion](locMadrid, locZaragoza, locValencia, locBarcelona, locSevilla)
  val locsAlmacen = Seq[Localizacion](locMadrid, locZaragoza, locValencia, locBarcelona, locSevilla)
  val rutas = Seq[Seq[Localizacion]](ruta1, ruta2)
  val capacidadesTrenes = Seq[Int](10,10)
  fabricaMaster ! IniciarFabricaMaster(locsFabrica)
  trenMaster ! IniciarTrenMaster(rutas, capacidadesTrenes)
  almacenMaster ! IniciarAlmacenMaster(locsAlmacen)
}
