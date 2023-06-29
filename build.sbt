val Scala213 = "2.13.11"
val Scala3 = "3.3.0"

ThisBuild / scalaVersion := Scala213
ThisBuild / crossScalaVersions := Seq(Scala213, Scala3)
ThisBuild / organization := "me.wojnowski"

inThisBuild(
  List(
    organization := "me.wojnowski",
    homepage := Some(url("https://github.com/jwojnowski/oidc4s")),
    licenses := List("MIT License" -> url("https://opensource.org/licenses/MIT")),
    developers := List(
      Developer(
        "jwojnowski",
        "Jakub Wojnowski",
        "29680262+jwojnowski@users.noreply.github.com",
        url("https://github.com/jwojnowski")
      )
    ),
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
  )
)

val commonSettings = Seq(
  makePom / publishArtifact := true
)

lazy val Versions = new {

  val cats = new {
    val core = "2.9.0"
    val effect = "3.5.1"
  }

  val circe = "0.14.5"

  val sttp = "3.8.15"

  val jwtScala = "9.4.0"

  val mUnit = "0.7.29"
  val mUnitCatsEffect = "1.0.7"

}

lazy val core = (project in file("core")).settings(
  commonSettings ++ Seq(
    name := "oidc4s-core",
    libraryDependencies += "org.typelevel" %% "cats-core" % Versions.cats.core,
    libraryDependencies += "org.typelevel" %% "cats-effect" % Versions.cats.effect,
    libraryDependencies += "com.github.jwt-scala" %% "jwt-core" % Versions.jwtScala,
    libraryDependencies += "org.scalameta" %% "munit" % Versions.mUnit % Test,
    libraryDependencies += "org.typelevel" %% "munit-cats-effect-3" % Versions.mUnitCatsEffect % Test,
    libraryDependencies += "org.typelevel" %% "cats-effect-testkit" % Versions.cats.effect % Test
  )
)

lazy val circe = (project in file("circe"))
  .settings(
    commonSettings ++ Seq(
      name := "oidc4s-circe",
      libraryDependencies += "io.circe" %% "circe-core" % Versions.circe,
      libraryDependencies += "io.circe" %% "circe-parser" % Versions.circe,
      libraryDependencies += "org.typelevel" %% "jawn-parser" % "1.5.1" // CVE-2022-21653
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val sttp = (project in file("sttp"))
  .settings(
    commonSettings ++ Seq(
      name := "oidc4s-sttp",
      libraryDependencies += "com.softwaremill.sttp.client3" %% "core" % Versions.sttp
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val quickSttpCirce = (project in file("quick-sttp-circe"))
  .settings(
    commonSettings ++ Seq(
      name := "oidc4s-quick-sttp-circe"
    )
  )
  .dependsOn(core, circe, sttp)

lazy val root = (project in file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(core, circe, sttp, quickSttpCirce)
