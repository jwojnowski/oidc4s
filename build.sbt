val Scala213 = "2.13.18"
val Scala3 = "3.7.4"

ThisBuild / tlBaseVersion := "0.13"

ThisBuild / scalaVersion := Scala213
ThisBuild / crossScalaVersions := Seq(Scala213, Scala3)
ThisBuild / organization := "me.wojnowski"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("jwojnowski", "Jakub Wojnowski")
)

ThisBuild / tlCiReleaseBranches := Seq()
ThisBuild / tlCiHeaderCheck := false
ThisBuild / tlCiScalafixCheck := true
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("21"))

val commonSettings = Seq(
  makePom / publishArtifact := true
)

lazy val Versions = new {

  val cats = new {
    val core = "2.13.0"
    val effect = "3.6.3"
  }

  val circe = "0.14.15"
  val zioJson = "0.9.0"

  val sttp3 = "3.11.0"
  val sttp4 = "4.0.19"

  val jwtScala = "9.4.4"

  val mUnit = "1.2.2"
  val mUnitCatsEffect = "2.1.0"
  val mUnitScalacheck = "1.2.0"

}

lazy val core = (project in file("core")).settings(
  commonSettings ++ Seq(
    name := "oidc4s-core",
    libraryDependencies += "org.typelevel" %% "cats-core" % Versions.cats.core,
    libraryDependencies += "org.typelevel" %% "cats-effect" % Versions.cats.effect,
    libraryDependencies += "org.scalameta" %% "munit" % Versions.mUnit % Test,
    libraryDependencies += "org.typelevel" %% "munit-cats-effect" % Versions.mUnitCatsEffect % Test,
    libraryDependencies += "org.typelevel" %% "cats-effect-testkit" % Versions.cats.effect % Test,
    libraryDependencies += "org.scalameta" %% "munit-scalacheck" % Versions.mUnitScalacheck % Test,
    libraryDependencies += "org.typelevel" %% "scalacheck-effect-munit" % "2.0.0-M2" % Test,
    libraryDependencies += "com.github.jwt-scala" %% "jwt-core" % "11.0.3" % Test
  )
)

lazy val circe = (project in file("circe"))
  .settings(
    commonSettings ++ Seq(
      name := "oidc4s-circe",
      libraryDependencies += "io.circe" %% "circe-core" % Versions.circe,
      libraryDependencies += "io.circe" %% "circe-parser" % Versions.circe,
      libraryDependencies += "org.typelevel" %% "jawn-parser" % "1.6.0" // CVE-2022-21653
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val zioJson = (project in file("zio-json"))
  .settings(
    commonSettings ++ Seq(
      name := "oidc4s-zio-json",
      libraryDependencies += "dev.zio" %% "zio-json" % Versions.zioJson,
      libraryDependencies ++= {
        if (scalaVersion.value.startsWith("2."))
          Seq("dev.zio" %% "zio-json-macros" % Versions.zioJson)
        else
          Seq.empty
      },
      mimaPreviousArtifacts := Set()
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val sttp3 = (project in file("sttp3"))
  .settings(
    commonSettings ++ Seq(
      name := "oidc4s-sttp",
      libraryDependencies += "com.softwaremill.sttp.client3" %% "core" % Versions.sttp3
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val sttp4 = (project in file("sttp4"))
  .settings(
    commonSettings ++ Seq(
      name := "oidc4s-sttp4",
      libraryDependencies += "com.softwaremill.sttp.client4" %% "core" % Versions.sttp4,
      mimaPreviousArtifacts := Set()
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val testkit = (project in file("testkit"))
  .settings(
    commonSettings ++ Seq(
      name := "oidc4s-testkit",
      libraryDependencies ++= Seq(
        "org.scalameta" %% "munit" % Versions.mUnit % Test
      )
    )
  )
  .dependsOn(core, circe % "test->compile")

lazy val quickSttp3Circe = (project in file("quick-sttp3-circe"))
  .settings(
    commonSettings ++ Seq(
      name := "oidc4s-quick-sttp-circe"
    )
  )
  .dependsOn(core, circe, sttp3)

lazy val quickSttp3ZioJson = (project in file("quick-sttp3-zio-json"))
  .settings(
    commonSettings ++ Seq(
      name := "oidc4s-quick-sttp-zio-json",
      mimaPreviousArtifacts := Set()
    )
  )
  .dependsOn(core, zioJson, sttp3)

lazy val quickSttp4Circe = (project in file("quick-sttp4-circe"))
  .settings(
    commonSettings ++ Seq(
      name := "oidc4s-quick-sttp4-circe",
      mimaPreviousArtifacts := Set()
    )
  )
  .dependsOn(core, circe, sttp4)

lazy val quickSttp4ZioJson = (project in file("quick-sttp4-zio-json"))
  .settings(
    commonSettings ++ Seq(
      name := "oidc4s-quick-sttp4-zio-json",
      mimaPreviousArtifacts := Set()
    )
  )
  .dependsOn(core, zioJson, sttp4)

lazy val root =
  tlCrossRootProject
    .settings(name := "oidc4s")
    .aggregate(
      core,
      circe,
      zioJson,
      sttp3,
      sttp4,
      quickSttp3Circe,
      quickSttp3ZioJson,
      quickSttp4Circe,
      quickSttp4ZioJson,
      testkit
    )
