name := "tfg-logistica-transporte"

version := "0.1"

scalaVersion := "2.13.6" // 2.12.7 2.13.6

val AkkaVersion = "2.5.32"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion,
  "org.scalatest" %% "scalatest" % "3.2.9",
  "com.github.nscala-time" %% "nscala-time" % "2.30.0"
)
