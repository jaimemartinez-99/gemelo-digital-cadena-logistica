name := "gemelo-digital-logistica-transporte"

version := "1.5"

scalaVersion := "2.13.7" // 2.12.7 2.13.6

val AkkaVersion = "2.6.19" // 2.5.32
val KafkaVersion = "3.1.0"
val JacksonVersion = "2.13.2.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion,
  "org.scalatest" %% "scalatest" % "3.2.11",
  "com.github.nscala-time" %% "nscala-time" % "2.30.0",
  "org.apache.kafka" % "kafka-clients" % KafkaVersion,
  "com.typesafe.akka" %% "akka-stream-kafka" % "3.0.0",
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % JacksonVersion,
  //"com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % "2.10.0",
  //"com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % "2.10.0",
  //"com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.10.0",
  //"com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
  //"ch.qos.logback" % "logback-classic" % "1.2.7" % Runtime
  //"ch.qos.logback" % "logback-classic" % "1.2.7"
  "com.lihaoyi" %% "requests" % "0.7.0",
  "com.typesafe.play" %% "play-json" % "2.9.2"
)
