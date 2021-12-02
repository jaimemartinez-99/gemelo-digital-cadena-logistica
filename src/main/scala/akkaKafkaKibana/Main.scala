package akkaKafkaKibana

import akka.actor.{ActorSystem, Props}
import akkaKafkaKibana.actors.Train

object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("trains")
    val r = scala.util.Random
    val producer = system.actorOf(Props[KafkaPublisher], "kafka_producer")
    (1 to r.nextInt(20) + 5).foreach(i => {
      val train = system.actorOf(Train.props(f"TRAIN$i", 2000 + r.nextDouble() * 10000), f"train$i")
      train ! Train.FindTheProducer(producer)
    })
  }
}
