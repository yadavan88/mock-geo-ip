val scala3Version = "3.6.4"
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := scala3Version

lazy val root = project
  .in(file("."))
  .aggregate(backend, frontend, shared)

lazy val backend = project
  .in(file("backend"))
  .settings(
    name := "mock-geoip-backend",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % "0.23.30",
      "org.http4s" %% "http4s-ember-client" % "0.23.30",
      "org.http4s" %% "http4s-dsl" % "0.23.30",
      "org.http4s" %% "http4s-circe" % "0.23.30",
      "com.ibm.icu" % "icu4j" % "77.1"
    ),
    Compile / resourceGenerators += Def.task {
      val frontendFiles = (frontend / Compile / fastOptJS).value
      val targetDir = (Compile / resourceDirectory).value / "assets"
      IO.createDirectory(targetDir)
      IO.copyFile(frontendFiles.data, targetDir / "main.js")
      Seq(targetDir)
    }.dependsOn(frontend / Compile / fastOptJS).taskValue
  ).dependsOn(shared)

lazy val frontend = project
  .in(file("frontend"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "mock-geoip-frontend",
    scalaJSUseMainModuleInitializer := true,
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.8.0",
      "com.lihaoyi" %%% "scalatags" % "0.12.0"
    )
  ).dependsOn(shared)

lazy val shared = project
  .in(file("shared"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "shared",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % "0.14.8",
      "io.circe" %%% "circe-generic" % "0.14.8",
      "io.circe" %%% "circe-parser" % "0.14.8"
    )
  )