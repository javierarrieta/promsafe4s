ThisBuild / organization := "io.github.javierarrieta"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"
ThisBuild / crossScalaVersions := Seq("2.13.18", "3.3.7")
ThisBuild / javacOptions ++= Seq("--release", "11")
Global / excludeLintKeys ++= Set(
  git.gitUncommittedChanges,
  git.gitDescribedVersion
)
ThisBuild / homepage := Some(uri("https://github.com/javierarrieta/promsafe4s"))
ThisBuild / description := "Typed Cats Effect wrappers around the Prometheus Java client"
ThisBuild / licenses := Seq("Apache-2.0" -> uri("https://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    uri("https://github.com/javierarrieta/promsafe4s"),
    "scm:git:git@github.com:javierarrieta/promsafe4s.git"
  )
)
ThisBuild / developers := List(
  Developer(
    "javierarrieta",
    "Javier Arrieta",
    "javierarrieta@users.noreply.github.com",
    uri("https://github.com/javierarrieta")
  )
)

lazy val catsVersion = "2.13.0"
lazy val catsEffectVersion = "3.7.0"
lazy val prometheusVersion = "1.8.0"

lazy val commonSettings = Seq(
  versionScheme := Some("early-semver"),
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core" % catsVersion,
    "org.typelevel" %% "cats-effect-kernel" % catsEffectVersion,
    "io.prometheus" % "prometheus-metrics-core" % prometheusVersion,
    "io.prometheus" % "prometheus-metrics-model" % prometheusVersion,
    "org.typelevel" %% "munit-cats-effect" % "2.2.0" % Test
  )
)

lazy val core = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(name := "promsafe4s")

lazy val derivation = project
  .in(file("derivation"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "promsafe4s-derivation",
    libraryDependencies ++= {
      if (scalaBinaryVersion.value == "3")
        Seq("com.softwaremill.magnolia1_3" %% "magnolia" % "1.3.18")
      else Seq.empty
    }
  )

lazy val root = project
  .in(file("."))
  .aggregate(core, derivation)
  .settings(publish / skip := true)
