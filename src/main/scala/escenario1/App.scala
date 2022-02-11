package escenario1

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

/**
 * Objeto para crear el programa ejecutable en Scala.
 * @author José Antonio Antona Díaz
 */

object App extends App {

  import SistemaMaster._

  // Parametros del fichero de configuración
  val parametros = ConfigFactory.load("application.conf")

  // Creacion del sistema
  val system = ActorSystem(s"${parametros.getString("nombreSistema")}")

  // Creacion del actor principal
  val sistemaMaster = system.actorOf(Props[SistemaMaster], s"${parametros.getString("nombreSistemaMaster")}")

  // Notificación al actor principal para la creación de los actores master
  sistemaMaster ! IniciarSistemaMaster(parametros)
}
