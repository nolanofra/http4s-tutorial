import LibraryDependencies._

inThisBuild(
  List(
    organization := "nolanofra",
    developers := List(
      Developer("nolanofra", "Francesco Nolano", "nolanofra@gmail.com", url("https://github.com/nolanofra"))
    ),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    pomIncludeRepository := { _ => false }
  )
)

val projectName = "http4s-tutorial"

lazy val projectSettings = Seq(
  name := projectName,
  organization := "com.nolanofra",
  scalaVersion := "2.13.3",
  scalafmtOnCompile := true,
)

libraryDependencies ++= Seq(
  http4sServer,
  http4sCirce,
  circeGeneric,
  http4sDsl
)

lazy val root = (project in file("."))
  .settings(projectSettings)

