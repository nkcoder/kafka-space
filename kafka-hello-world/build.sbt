ThisBuild / scalaVersion := "3.7.4"
ThisBuild / version := "0.1.0"
ThisBuild / organization := "org.nkcoder"

lazy val root = (project in file("."))
  .settings(
    name := "kafka-quickstart",
    libraryDependencies ++= Seq(
      // Kafka clients - use the latest 4.1.x version
      "org.apache.kafka" % "kafka-clients" % "4.1.1",

      // Logging
      "ch.qos.logback" % "logback-classic" % "1.5.23",
      "org.slf4j" % "slf4j-api" % "2.0.17",

      // JSON serialization (optional, for structured messages)
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.20.1",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.20.1"
    )
  )