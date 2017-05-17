// Turn this project into a Scala.js project by importing these settings

import sbt.Keys._
import spray.revolver.AppProcess
import spray.revolver.RevolverPlugin.Revolver

scalaVersion := "2.11.8"

val circeVersion = "0.7.0"

val pviz = crossProject.settings(
  scalaVersion := "2.11.8",
  version := "0.1-SNAPSHOT",
  libraryDependencies ++= Seq(
    "com.lihaoyi" %%% "upickle" % "0.4.3",
    "com.lihaoyi" %%% "autowire" % "0.2.6",
    "com.lihaoyi" %%% "scalatags" % "0.6.1"
  )
).jsSettings(
  name := "Client",
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.1",
    "be.doeraene" %%% "scalajs-jquery" % "0.9.1"
  )
).jvmSettings(
  Revolver.settings:_*
).jvmSettings(
  name := "Server",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-http-experimental" % "2.4.11.2",
    "com.typesafe.akka" %% "akka-actor" % "2.4.12",
    "org.webjars" % "bootstrap" % "3.2.0",
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion
  )
)

val pvizJS = pviz.js
val pvizJVM = pviz.jvm.settings(
  (resources in Compile) += {
    (fastOptJS in (pvizJS, Compile)).value
    (artifactPath in (pvizJS, Compile, fastOptJS)).value
  }
)
