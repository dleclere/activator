import sbt._
import Keys._

object Dependencies {
  val sbtVersion = "0.13.8"
  val sbtLibraryVersion = "0.13.8" // for sbtIO on scala 2.11

  val sbtPluginVersion = "0.13"
  val sbtPluginScalaVersion = "2.11.7"
  val scalaVersion = "2.11.7"
  val scala210Version = "2.10.4"
  val luceneVersion = "4.2.1"

  val templateCacheVersion = "1.0-3c50ec793f424aca7a39338af78a3664019482bc"
  val sbtRcVersion = "0.3.5"
  val sbtCoreNextVersion = "0.1.1"

  val shimPlayVersion = "2.4.2"
  val play23Version = "2.3.9"
  val akka22Version = "2.2.4"
  val akka23Version = "2.3.11"
  val slickVersion = "3.0.0"
  val activatorAnalyticsVersion = "0.1.8"
  val aspectJVersion = "1.8.4"

  // versions used by build to set dependencies in JS
  val ideaVersion = "1.6.0"
  val eclipseVersion = "4.0.0"

  val activatorCommon      = "com.typesafe.activator" % "activator-common" % templateCacheVersion
  val templateCache        = "com.typesafe.activator" % "activator-templates-cache" % templateCacheVersion

  // 2.10 sbt
  val sbtIo210             = "org.scala-sbt" % "io" % sbtVersion
  // launcher interface is pure Java, no scala versioning
  val sbtLauncherInterface = "org.scala-sbt" % "launcher-interface" % sbtVersion

  // 2.11 sbt
  val sbtIo                = "org.scala-sbt" %% "io" % sbtLibraryVersion
  val sbtCompletion        = "org.scala-sbt" %% "completion" % sbtLibraryVersion

  // sbtrc projects
  val sbtrcClient          = "com.typesafe.sbtrc" % "client-2-11" % sbtRcVersion
  val sbtrcIntegration     = "com.typesafe.sbtrc" % "integration-tests" % sbtRcVersion

  val akkaActor            = "com.typesafe.akka" %% "akka-actor" % akka23Version
  val akkaSlf4j            = "com.typesafe.akka" %% "akka-slf4j" % akka23Version
  val akkaTestkit          = "com.typesafe.akka" %% "akka-testkit"% akka23Version

  val commonsIo            = "commons-io" % "commons-io" % "2.0.1"

  val mimeUtil             = "eu.medsea.mimeutil" % "mime-util" % "2.1.3" exclude("org.slf4j", "slf4j-log4j12") exclude("org.slf4j", "slf4j-api") exclude("log4j", "log4j")

  val junitInterface       = "com.novocode" % "junit-interface" % "0.7"
  val specs2               = "org.specs2" % "specs2_2.11" % "2.3.11"

  // SBT 0.13 required plugins
  val playSbt13Plugin        =  Defaults.sbtPluginExtra("com.typesafe.play" % "sbt-plugin" % shimPlayVersion, "0.13", "2.10")
  val eclipseSbt13Plugin     =  Defaults.sbtPluginExtra("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "4.0.0", "0.13", "2.10")
  val ideaSbt13Plugin        =  Defaults.sbtPluginExtra("com.github.mpeltonen" % "sbt-idea" % "1.5.2", "0.13", "2.10")

  // Embedded databases / index
  val lucene = "org.apache.lucene" % "lucene-core" % luceneVersion
  val luceneAnalyzerCommon = "org.apache.lucene" % "lucene-analyzers-common" % luceneVersion
  val luceneQueryParser = "org.apache.lucene" % "lucene-queryparser" % luceneVersion

  // WebJars for the Activator UI
  val requirejs        = "org.webjars" % "requirejs" % "2.1.11"
  val jquery           = "org.webjars" % "jquery" % "2.0.3"
  val knockout         = "org.webjars" % "knockout" % "3.3.0"
  val ace              = "org.webjars" % "ace" % "1.1.7-1"
  val keymage          = "org.webjars" % "keymage" % "1.0.1"

  // Analyzers used by Inspect
  val activatorAnalytics = "com.typesafe.activator"  %% "analytics" % activatorAnalyticsVersion

  // Mini DSL
  // DSL for adding remote deps like local deps.
  implicit def p2remote(p: Project): RemoteDepHelper = new RemoteDepHelper(p)
  final class RemoteDepHelper(p: Project) {
    def dependsOnRemote(ms: ModuleID*): Project = p.settings(libraryDependencies ++= ms)
  }
  // DSL for adding source dependencies ot projects.
  def dependsOnSource(dir: String): Seq[Setting[_]] = {
    import Keys._
    Seq(unmanagedSourceDirectories in Compile <<= (unmanagedSourceDirectories in Compile, baseDirectory) { (srcDirs, base) => (base / dir / "src/main/scala") +: srcDirs },
        unmanagedSourceDirectories in Test <<= (unmanagedSourceDirectories in Test, baseDirectory) { (srcDirs, base) => (base / dir / "src/test/scala") +: srcDirs })
  }
  implicit def p2source(p: Project): SourceDepHelper = new SourceDepHelper(p)
  final class SourceDepHelper(p: Project) {
    def dependsOnSource(dir: String): Project =
      p.settings(Dependencies.dependsOnSource(dir):_*)
  }

  // compile classpath and classes directory, with provided/optional or scala dependencies
  // specifically for projects that need remote-probe dependencies
  val requiredClasspath = TaskKey[Classpath]("required-classpath")

  def requiredJars(deps: ProjectReference*): Setting[_] = {
    import xsbti.ArtifactInfo._
    import Project.Initialize
    val dependentProjectClassPaths: Seq[Initialize[Task[Seq[File]]]] =
      (deps map { proj =>
        (classDirectory in Compile in proj) map { dir => Seq(dir) }
      })
    val ivyDeps: Initialize[Task[Seq[File]]] =  update map { report =>
      val jars = report.matching(configurationFilter(name = "compile") -- moduleFilter(organization = ScalaOrganization, name = ScalaLibraryID))
      jars
    }
    val localClasses: Initialize[Task[Seq[File]]] = (classDirectory in Compile) map { dir =>
      Seq(dir)
    }
    // JOin everyone
    def joinCp(inits: Seq[Initialize[Task[Seq[File]]]]): Initialize[Task[Seq[File]]] =
      inits reduce { (lhs, rhs) =>
        (lhs zip rhs).flatMap { case (l,r) =>
          l.flatMap[Seq[File]] { files =>
            r.map[Seq[File]] { files2 =>
              files ++ files2
            }
          }
        }
      }
    requiredClasspath <<= joinCp(dependentProjectClassPaths ++ Seq(ivyDeps, localClasses)) map {
      _.classpath
    }
  }

  val sbtBackgroundRun = Defaults.sbtPluginExtra("org.scala-sbt" % "sbt-core-next" % sbtCoreNextVersion, "0.13", "2.10")

  def playPlugin: Seq[Setting[_]] = Seq(
    resolvers += Classpaths.typesafeSnapshots,
    resolvers += "Typesafe Maven Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
    resolvers += "Typesafe Maven Releases" at "http://repo.typesafe.com/typesafe/releases/"
  )
  // *** END SBT-ECHO DEPENDENCIES ***

}
