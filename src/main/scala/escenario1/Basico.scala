package escenario1

/**
 * @author José Antonio Antona Díaz
 */

object Basico {

  // Paquete
  case class Paquete (id: Int, prioridad: Int, cliente: Cliente, localizacionDestino: Localizacion)

  // Cliente
  case class Cliente (id: Int, name: String)

  // Localizacion
  case class Localizacion (id: Int, name: String)

}
