ThisBuild / organization := "co.fs2"

ThisBuild / scalaVersion := "3.3.6"
ThisBuild / crossScalaVersions := List("3.3.6", "2.13.16")

ThisBuild / tlBaseVersion := "0.1"

ThisBuild / startYear := Some(2025)
ThisBuild / licenses := List(("MIT", url("http://opensource.org/licenses/MIT")))

lazy val root = tlCrossRootProject.aggregate(core)

lazy val core = crossProject(JVMPlatform, JSPlatform) // NativePlatform) // Need otel4s release for CE 3.7
  .in(file("core"))
  .settings(
    name := "fs2-instrumentation",
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-io" % "3.13.0-M6",
      "org.typelevel" %%% "otel4s-core" % "0.13.1",
      "org.typelevel" %%% "otel4s-sdk" % "0.13.1" % Test,
      "org.typelevel" %%% "otel4s-sdk-metrics-testkit" % "0.13.1" % Test,
      "org.typelevel" %%% "munit-cats-effect" % "2.2.0-RC1" % Test,
      "org.typelevel" %%% "scalacheck-effect-munit" % "2.1.0-RC1" % Test
    )
  )
  .jsSettings(
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  )
