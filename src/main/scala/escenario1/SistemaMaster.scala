package escenario1

import akka.actor.{Actor, ActorLogging, Props}
import com.github.nscala_time.time.Imports.DateTimeZone
import com.typesafe.config.Config
import escenario1.Basico.{Cliente, Estacion, Localizacion}
import org.joda.time.DateTime
import scala.util.Try


/**
 * @author José Antonio Antona Díaz
 * @author Mario Esperalta Delgado
 */

object SistemaMaster {
  case class IniciarSistemaMaster(parametros: Config)
}

class SistemaMaster extends Actor with ActorLogging {
  import SistemaMaster._
  import AlmacenMaster._
  import TrenMaster._
  import FabricaMaster._

  override def receive: Receive = {
    case IniciarSistemaMaster(parametros) =>
      log.debug(s"[SistemaMaster] Iniciando el sistema")

      // Creacion de los actores master
      val fabricaMaster = context.actorOf(Props[FabricaMaster], s"${parametros.getString("nombreFabricaMaster")}")
      val trenMaster = context.actorOf(Props[TrenMaster], s"${parametros.getString("nombreTrenMaster")}")
      val almacenMaster = context.actorOf(Props[AlmacenMaster], s"${parametros.getString("nombreAlmacenMaster")}")
      val producer = context.actorOf(Props[KafkaPublisher], "kafka_producer")

      // Inicializacion de las localizaciones, estaciones, rutas, capacidades, clientes
      val nombresLocalizaciones = parametros.getStringList("localizaciones").toArray.toList //application.conf
      val numeroEstaciones = parametros.getObject("estaciones").size()
      val locsNombreFabrica = parametros.getStringList("locsFabrica").toArray.toList
      val locsNombreAlmacen = parametros.getStringList("locsFabrica").toArray.toList
      val nombresClientes = parametros.getStringList("clientes").toArray.toList
      val factorVelocidad = parametros.getInt("factorVelocidad")
      val numeroRutas = parametros.getObject("rutas").size
      val numeroCapacidades = parametros.getObject("capacidadesTrenes").size

      var localizaciones = Seq[Localizacion]()
      for (i <- nombresLocalizaciones.indices) {
        localizaciones = localizaciones :+ Localizacion(i+1, s"${nombresLocalizaciones(i)}")
      }

      var estaciones = Seq[Estacion]()
      for (i <- 1 to numeroEstaciones) {
        val strings = parametros.getStringList(s"estaciones.estacion$i").toArray.toList
        val ciuString = strings.head.toString
        val estString = strings.tail.head.toString
        val longString = strings.tail.tail.head.toString
        val latString = strings.tail.tail.tail.head.toString
        estaciones = estaciones :+ Estacion(i, ciuString, estString, longString, latString)
      }

      var rutas = Seq[Seq[Localizacion]]()
      for (i <- 1 to numeroRutas) {
        val rutaId = parametros.getIntList(s"rutas.ruta$i").toArray.toList
        var rutaLoc = Seq[Localizacion]()
        rutaId.foreach(locId => rutaLoc = rutaLoc :+ localizaciones(locId.toString.toInt-1))
        rutas = rutas :+ rutaLoc
      }

      var capacidadesTrenes = Seq[Int]()
      for (i <- 1 to numeroCapacidades) {
        val capacidadTren = parametros.getInt(s"capacidadesTrenes.capacidadTren$i")
        capacidadesTrenes = capacidadesTrenes :+ capacidadTren
      }

      var locsFabrica = Seq[Localizacion]()
      for (i <- locsNombreFabrica.indices){
        if(localizaciones(i).name == locsNombreFabrica(i).toString){
          locsFabrica = locsFabrica :+ Localizacion(i+1, s"${locsNombreFabrica(i)}")
        }
      }

      var locsAlmacen = Seq[Localizacion]()
      for (i <- locsNombreAlmacen.indices){
        if(localizaciones(i).name == locsNombreAlmacen(i).toString){
          locsAlmacen =  locsAlmacen :+ Localizacion(i+1, s"${locsNombreAlmacen(i)}")
        }
      }

      var clientes = Seq[Cliente]()
      for (i <- nombresClientes.indices) {
        clientes = clientes :+ Cliente(i+1, s"${nombresClientes(i)}")
      }

      // DateTime inicial
      val initialDT = new DateTime(
        parametros.getInt("dateTime.year"),
        parametros.getInt("dateTime.month"),
        parametros.getInt("dateTime.day"),
        parametros.getInt("dateTime.hour"),
        parametros.getInt("dateTime.minute"),
        parametros.getInt("dateTime.second"),
        DateTimeZone.forOffsetHours(parametros.getInt("dateTime.dateTimeZone"))
      )

      // DateTime actual
      val actualDT = DateTime.now

      // Notificación para la creación de los actores fábrica, tren y almacén que componen el escenario
      fabricaMaster ! IniciarFabricaMaster(locsFabrica, factorVelocidad, initialDT, actualDT, clientes, localizaciones, producer)
      trenMaster ! IniciarTrenMaster(estaciones, rutas, capacidadesTrenes, factorVelocidad, initialDT, actualDT, fabricaMaster, almacenMaster, producer)
      almacenMaster ! IniciarAlmacenMaster(locsAlmacen, factorVelocidad, initialDT, actualDT)
  }

}