ThisBuild / scalaVersion := "2.13.14"
ThisBuild / organization := "local.chisel"
ThisBuild / version := "0.1.0"

val chiselVersion = "6.5.0"

lazy val root = (project in file("."))
  .settings(
    name := "SZCORE",
    libraryDependencies += "org.chipsalliance" %% "chisel" % chiselVersion,
    addCompilerPlugin(
      "org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full
    )
  )