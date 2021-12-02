package akkaKafkaKibana

import akka.actor.{Actor, ActorSystem}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.SendProducer
import com.typesafe.config.Config
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer

class KafkaPublisher extends Actor {
  implicit val sys: ActorSystem = context.system
  val config: Config = context.system.settings.config.getConfig("akka.kafka.producer")
  val producerSettings: ProducerSettings[String, String] =
    ProducerSettings(config, new StringSerializer, new StringSerializer)
      .withBootstrapServers("localhost:9093")
  val producer: SendProducer[String, String] = SendProducer(producerSettings)

  override def receive: Receive = {
    case x:String => producer.send(new ProducerRecord("simulator", x))
    case _ =>
  }
}
