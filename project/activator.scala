import sbt._
import Keys._
import play.PlayScala
import com.typesafe.sbt.less.Import.LessKeys
import com.typesafe.sbt.web.SbtWeb
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.SbtGit
import com.typesafe.sbt.SbtPgp
import com.typesafe.sbt.SbtPgp.autoImport._
import bintray.Plugin.bintrayPublishSettings
import bintray.Keys._


object ActivatorBuild {
  // Don't calculate versions EVERYWHERE, just in global...
  def baseVersions: Seq[Setting[_]] = SbtGit.versionWithGit

  def formatPrefs = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
      .setPreference(IndentSpaces, 2)
  }

  val typesafeIvyReleases = Resolver.url("typesafe-ivy-private-releases", new URL("https://private-repo.typesafe.com/typesafe/ivy-releases/"))(Resolver.ivyStylePatterns)

  private val fixWhitespace = TaskKey[Seq[File]]("fix-whitespace")

  private def makeFixWhitespace(config: Configuration): Setting[_] = {
    fixWhitespace in config <<= (unmanagedSources in config, streams) map { (sources, streams) =>
      for (s <- sources) {
        Fixer.fixWhitespace(s, streams.log)
      }
      sources
    }
  }

  def activatorDefaults: Seq[Setting[_]] =
    SbtScalariform.scalariformSettings ++
    bintrayPublishSettings ++
    Seq(
      organization := "com.typesafe.activator",
      version <<= version in ThisBuild,
      crossPaths := false,
      resolvers += "typesafe-mvn-releases" at "https://repo.typesafe.com/typesafe/releases/",
      resolvers += Resolver.url("typesafe-ivy-releases", new URL("https://repo.typesafe.com/typesafe/releases/"))(Resolver.ivyStylePatterns),
      publishMavenStyle := false,
      publish := { throw new RuntimeException("use publishSigned instead of plain publish") },
      bintrayOrganization in bintray := Some("typesafe"),
      repository in bintray := "ivy-releases",
      licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
      scalacOptions <<= (scalaVersion) map { sv =>
        Seq("-unchecked", "-deprecation") ++
          { if (sv.startsWith("2.9")) Seq.empty else Seq("-feature") }
      },
      javacOptions in Compile := Seq("-target", "1.6", "-source", "1.6"),
      javacOptions in (Compile, doc) := Seq("-source", "1.6"),
      libraryDependencies += Dependencies.junitInterface % "test",
      scalaVersion := Dependencies.scalaVersion,
      scalaBinaryVersion := "2.11",
      ScalariformKeys.preferences in Compile := formatPrefs,
      ScalariformKeys.preferences in Test    := formatPrefs,
      makeFixWhitespace(Compile),
      makeFixWhitespace(Test),
      compileInputs in (Compile, compile) <<= (compileInputs in (Compile, compile)) dependsOn (fixWhitespace in Compile),
      compileInputs in (Test, compile) <<= (compileInputs in (Test, compile)) dependsOn (fixWhitespace in Test)
    ) ++ JavaVersionCheck.javacVersionCheckSettings ++ SbtPgp.projectSettings ++
    net.virtualvoid.sbt.graph.Plugin.graphSettings ++
    // these have to be after SbtPgp.settings
    Seq(
      PgpKeys.publishSigned := {
        val log = Keys.streams.value.log
        val hash = (LocalTemplateRepo.checkTemplateCacheHashs in TheActivatorBuild.localTemplateRepo).value
        log.info("Will publish with template index " + hash)
        PgpKeys.publishSigned.value
      },
      PgpKeys.publishLocalSigned := {
        val log = Keys.streams.value.log
        val hash = (LocalTemplateRepo.checkTemplateCacheHashs in TheActivatorBuild.localTemplateRepo).value
        log.info("Will publish locally with template index " + hash)
        PgpKeys.publishLocalSigned.value
      })

  def sbtShimPluginSettings: Seq[Setting[_]] =
    activatorDefaults ++
    Seq(
      scalaVersion := Dependencies.sbtPluginScalaVersion,
      scalaBinaryVersion := Dependencies.sbtPluginScalaVersion,
      sbtPlugin := true,
      publishMavenStyle := false
    )

  implicit class NoAutoPgp(val project: Project) extends AnyVal {
    def noAutoPgp: Project = {
      // the default is autoSettings(autoPlugins, buildScalaSettings, userSettings, nonAutoPlugins, defaultSbtFiles)
      // userSettings = Project.settings
      // we want to push PGP before userSettings so we can override
      // publishSigned and publishLocalSigned,
      // but we leave other plugins alone to avoid confusing ourselves.
      def isPgp(plugin: Plugin): Boolean =
        plugin.getClass.getName.startsWith("com.typesafe.sbt.SbtPgp")
      import AddSettings._
      project.settingSets(
        autoPlugins,
        buildScalaFiles,
        userSettings,
        plugins(!isPgp(_)),
        defaultSbtFiles)
    }
  }

  implicit class DoNotPublish(val project: Project) extends AnyVal {
    def doNotPublish: Project = {
      project.settings(
        // this won't work if the project doesn't have PGP relocated (see above)
        PgpKeys.publishSigned := { streams.value.log(s"publishSigned disabled for ${name.value}") },
        PgpKeys.publishLocalSigned := { streams.value.log(s"publishLocalSigned disabled for ${name.value}") },
        publish := { streams.value.log(s"publish disabled for ${name.value}") },
        publishLocal := { streams.value.log(s"publishLocal disabled for ${name.value}") }
      )
    }
  }

  def toReferences(projects: Seq[Project]): Seq[ProjectReference] =
    NoProjectImplicitsHere.toReferences(projects)

  def ActivatorProject(name: String): Project = (
    Project("activator-" + name, file(name))
    .noAutoPgp
    settings(activatorDefaults:_*)
  )

  def ActivatorPlayProject(name: String): Project = (
    Project("activator-" + name, file(name))
    .enablePlugins(PlayScala, SbtWeb)
    .noAutoPgp
    settings(libraryDependencies ++= Seq(
      "com.typesafe.play" %% "filters-helpers" % play.core.PlayVersion.current,
      "com.typesafe.play" %% "play-ws" % play.core.PlayVersion.current))
    settings(LessKeys.verbose := true)
    settings(activatorDefaults:_*)
  )

  def ActivatorJavaProject(name: String): Project = (
    Project("activator-" + name, file(name))
    .noAutoPgp
    settings(activatorDefaults:_*)
    settings(autoScalaLibrary := false)
  )
}

// The ".project" macro on Project somehow breaks
// if invoked with the DoNotPublish / RelocatePgp
// implicits in scope, so this is a hack.
// Obviously there's some better fix somewhere.
private object NoProjectImplicitsHere {
  def toReferences(projects: Seq[Project]): Seq[ProjectReference] =
    projects.map(_.project)
}
