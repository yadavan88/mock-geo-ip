val scala3Version = "3.6.4"
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := scala3Version

lazy val root = project
  .in(file("."))
  .aggregate(backend, frontend, shared)

lazy val backend = project
  .in(file("backend"))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    name := "mock-geoip-backend",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % "0.23.30",
      "org.http4s" %% "http4s-ember-client" % "0.23.30",
      "org.http4s" %% "http4s-dsl" % "0.23.30",
      "org.http4s" %% "http4s-circe" % "0.23.30",
      "com.ibm.icu" % "icu4j" % "77.1",
      "ch.qos.logback" % "logback-classic" % "1.4.14",
      "org.slf4j" % "slf4j-api" % "2.0.9"
    ),
    Compile / resourceGenerators += Def.task {
      val frontendFiles = (frontend / Compile / fastOptJS).value
      val frontendHtml = (frontend / Compile / resourceDirectory).value / "index.html"
      val targetDir = (Compile / resourceDirectory).value
      val assetsDir = targetDir / "assets"
      IO.createDirectory(assetsDir)
      IO.copyFile(frontendFiles.data, assetsDir / "main.js")
      IO.copyFile(frontendHtml, targetDir / "index.html")
      Seq(targetDir)
    }.dependsOn(frontend / Compile / fastOptJS).taskValue,
    // Docker settings
    Docker / packageName := "mock-geoip",
    Docker / version := "latest",
    dockerBaseImage := "eclipse-temurin:17-jre",
    dockerExposedPorts := Seq(9050),
    dockerUpdateLatest := true
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