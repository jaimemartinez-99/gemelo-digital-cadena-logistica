package akkaKafkaKibana.actors

import akka.actor.{Actor, ActorRef, Props}

import scala.concurrent.duration._
import scala.language.postfixOps

object Train {
  def props(name: String, routeDuration: Double): Props = Props(new Train(name, routeDuration))
  case class FindTheProducer(ref: ActorRef)
}
class Train (name: String, routeDuration: Double) extends Actor {
  import Train._
  val r = scala.util.Random
  import context.dispatcher
  var producer: ActorRef = _
  def receive = {
    case "start" =>
      producer ! f"""{"source":"$name", "event": "start travel"}"""
      context.system.scheduler.scheduleOnce(routeDuration*(0.5-r.nextDouble()/10) millis, self, "end")
    case "end" =>
      producer ! f"""{"source":"$name", "event": "end travel"}"""
      context.system.scheduler.scheduleOnce(routeDuration*(0.5-r.nextDouble()/10) millis, self, "start")
    case x:FindTheProducer =>
      producer = x.ref
      self ! "start"
  }
}
