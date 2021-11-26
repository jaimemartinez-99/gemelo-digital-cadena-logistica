package escenario1

import akka.actor.{ActorSystem, Props}
import com.github.nscala_time.time.Imports.DateTimeZone
import com.typesafe.config.ConfigFactory
import escenario1.Basico.Localizacion
import org.joda.time.DateTime

object App extends App {

  // Parametros del fichero de configuración
  //val parametros = ConfigFactory.load("parametros.json")
  val parametros = ConfigFactory.load("application.conf")

  // Creacion del sistema
  val system = ActorSystem(s"${parametros.getString("nombreSistema")}")

  // Creacion de los actores principales
  val fabricaMaster = system.actorOf(Props[FabricaMaster], s"${parametros.getString("nombreFabricaMaster")}")
  val trenMaster = system.actorOf(Props[TrenMaster], s"${parametros.getString("nombreTrenMaster")}")
  val almacenMaster = system.actorOf(Props[AlmacenMaster], s"${parametros.getString("nombreAlmacenMaster")}")

  import FabricaMaster._
  import TrenMaster._
  import AlmacenMaster._

  // Inicializacion de las localizaciones, rutas y actores principales
  val nombresLocalizaciones = parametros.getStringList("localizaciones").toArray.toList //application.conf
  val numeroRutas = parametros.getObject("rutas").size
  val numeroCapacidades = parametros.getObject("capacidadesTrenes").size

  var localizaciones = Seq[Localizacion]()
  for (i <- nombresLocalizaciones.indices) {
    localizaciones = localizaciones :+ Localizacion(1, s"${nombresLocalizaciones(i)}")
  }

  /*
  val locMadrid = Localizacion(1,"Madrid")
  val locZaragoza = Localizacion(1,"Zaragoza")
  val locValencia = Localizacion(1,"Valencia")
  val locBarcelona = Localizacion(1,"Barcelona")
  val locSevilla = Localizacion(1,"Sevilla")

  val ruta1 = Seq[Localizacion](locMadrid, locZaragoza, locBarcelona)
  val ruta2 = Seq[Localizacion](locValencia, locMadrid, locSevilla)
  val rutas1 = Seq[Seq[Localizacion]](ruta1, ruta2)

  val capacidadesTrenes1 = Seq[Int](10,10)

   */

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

  val locsNombreFabrica = parametros.getStringList("locsFabrica").toArray.toList
  var locsFabrica = Seq[Localizacion]()
  for (i <- locsNombreFabrica.indices){
    if(localizaciones(i).name == locsNombreFabrica(i).toString){
      locsFabrica = locsFabrica :+ Localizacion(1, s"${locsNombreFabrica(i)}")
    }
  }

  val locsNombreAlmacen = parametros.getStringList("locsFabrica").toArray.toList
  var locsAlmacen = Seq[Localizacion]()
  for (i <- locsNombreAlmacen.indices){
    if(localizaciones(i).name == locsNombreAlmacen(i).toString){
      locsAlmacen =  locsAlmacen :+ Localizacion(1, s"${locsNombreAlmacen(i)}")
    }
  }

  val factorVelocidad = parametros.getInt("factorVelocidad")

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

  fabricaMaster ! IniciarFabricaMaster(locsFabrica, factorVelocidad, initialDT, actualDT)
  trenMaster ! IniciarTrenMaster(rutas, capacidadesTrenes, factorVelocidad, initialDT, actualDT)
  almacenMaster ! IniciarAlmacenMaster(locsAlmacen, initialDT, actualDT)
}
