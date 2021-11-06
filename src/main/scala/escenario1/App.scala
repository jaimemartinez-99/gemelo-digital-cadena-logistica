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
