package escenario1

import akka.actor.{Actor, ActorLogging, Props}
import com.github.nscala_time.time.Imports.DateTimeZone
import com.typesafe.config.Config
import escenario1.Basico.Localizacion
import escenario1.Basico.Cliente
import org.joda.time.DateTime

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

      // Creacion de los actores principales
      val fabricaMaster = context.actorOf(Props[FabricaMaster], s"${parametros.getString("nombreFabricaMaster")}")
      val trenMaster = context.actorOf(Props[TrenMaster], s"${parametros.getString("nombreTrenMaster")}")
      val almacenMaster = context.actorOf(Props[AlmacenMaster], s"${parametros.getString("nombreAlmacenMaster")}")

      // Inicializacion de las localizaciones, rutas y actores principales
      val nombresLocalizaciones = parametros.getStringList("localizaciones").toArray.toList //application.conf
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

      // Initial DateTime
      val initialDT = new DateTime(
        parametros.getInt("dateTime.year"),
        parametros.getInt("dateTime.month"),
        parametros.getInt("dateTime.day"),
        parametros.getInt("dateTime.hour"),
        parametros.getInt("dateTime.minute"),
        parametros.getInt("dateTime.second"),
        DateTimeZone.forOffsetHours(parametros.getInt("dateTime.dateTimeZone"))
      )

      val actualDT = DateTime.now

      fabricaMaster ! IniciarFabricaMaster(locsFabrica, factorVelocidad, initialDT, actualDT, clientes, localizaciones)
      trenMaster ! IniciarTrenMaster(rutas, capacidadesTrenes, factorVelocidad, initialDT, actualDT, fabricaMaster, almacenMaster)
      almacenMaster ! IniciarAlmacenMaster(locsAlmacen, factorVelocidad, initialDT, actualDT)
  }

}

