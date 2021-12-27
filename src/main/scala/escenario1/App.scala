package escenario1

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

object App extends App {

  import SistemaMaster._

  // Parametros del fichero de configuración
  val parametros = ConfigFactory.load("application.conf")

  // Creacion del sistema
  val system = ActorSystem(s"${parametros.getString("nombreSistema")}")

  // Creacion de los actores principales
  val sistemaMaster = system.actorOf(Props[SistemaMaster], s"${parametros.getString("nombreSistemaMaster")}")

  sistemaMaster ! IniciarSistemaMaster(parametros)
}
