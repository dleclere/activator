import sbt._
import Keys._

// Defines how to generate properties file based on build attributes.
object Properties {

  val makePropertiesSource = TaskKey[Seq[File]]("make-properties-source")
  val configVersion = taskKey[String]("version to store the config file")
  val previousConfigVersion = taskKey[String]("OLD version for the config file (to migrate from)")
  val launcherGeneration = taskKey[Int]("defines a universe of launchers to upgrade within")

  def writeIfChanged(file: java.io.File, content: String): Unit = {
    val oldContent = if (file.exists) IO.read(file) else ""
    if (oldContent != content) {
      IO.write(file, content)
    }
  }

  private def pickLauncherGeneration(version: String): Int = {
    // if we're building a git snapshot, we don't want to ever downgrade
    // to "latest" according to typesafe.com
    val hyphenHex = ".*-([a-f0-9]+)$".r
    version match {
      case hyphenHex(gitCommit) if gitCommit.length == 40 =>
        123456789
      case _ =>
          // our actual current generation if it's not a snapshot
        1
    }
  }

  def makePropertyClassSetting(sbtDefaultVersion: String, scalaVersion: String): Seq[Setting[_]] = Seq(
    resourceGenerators in Compile <+= makePropertiesSource,
    configVersion := "1.0", // all 1.0 variants share config format; if changing this, move the old one down to previousConfigVersion
    previousConfigVersion := "1.0.7", // if we see this config directory, upgrade from it
    launcherGeneration := pickLauncherGeneration(version.value),
    makePropertiesSource <<= (version, resourceManaged in Compile, compile in Compile, configVersion, previousConfigVersion, launcherGeneration) map { (version, dir, analysis, configVersion, previousConfigVersion, launcherGeneration) =>
      val parent= dir / "activator" / "properties"
      IO createDirectory parent
      val target = parent / "activator.properties"
      writeIfChanged(target, makeJavaPropertiesString(version, sbtDefaultVersion, scalaVersion, configVersion, previousConfigVersion, launcherGeneration))
      Seq(target)
    }
  )

  
  def lastCompilationTime(analysis: sbt.inc.Analysis): Long = {
    val lastCompilation = analysis.compilations.allCompilations.lastOption
    lastCompilation.map(_.startTime) getOrElse 0L
  }
  

  def makeJavaPropertiesString(version: String, sbtDefaultVersion: String, scalaVersion: String, configVersion: String, previousConfigVersion: String, launcherGeneration: Int): String = {
    """|app.version=%s
       |sbt.default.version=%s
       |app.scala.version=%s
       |app.config.version=%s
       |app.config.previousVersion=%s
       |sbt.Xmx=512M
       |sbt.PermSize=128M
       |activator.launcher.generation=%d
       |""".stripMargin format (version, sbtDefaultVersion, scalaVersion, configVersion, previousConfigVersion, launcherGeneration)
  }
}
