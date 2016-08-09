

def clearConsoleCommand = Command.command("clear") { state =>
  // TODO: find out why this doesn't work anymore
  val cr = new jline.console.ConsoleReader()
  cr.clearScreen
  state
}

val commonSettings = Seq(
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq("-feature", "-unchecked", "-deprecation"),
  commands += clearConsoleCommand,
  scalafmtConfig := Some(file(".scalafmt"))
) ++ reformatOnCompileSettings

val macros = project.in(file("macros")).settings(commonSettings).settings(
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value
)

val root = project.in(file(".")).settings(commonSettings).settings(
  name := """LMS-debugger""",
  version := "0.1",
  initialCommands := """
    import org.lmsdbg._
    import Main._
    import utils._
    import Printer._
    import DynamicWrappers._
    implicit val vm = attach()
    LMSInfo.init
    import scala.language.postfixOps
    import Localizers._
    import LMSInfo._
    import org.scaladebugger.api.profiles.traits.info.ValueInfoProfile
  """,
  cleanupCommands := "detach()",
  libraryDependencies += "org.scala-debugger" %% "scala-debugger-api" % "1.1.0-M1"
).dependsOn(macros)
