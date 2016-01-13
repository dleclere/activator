import sbt._
import Keys._
import SbtSupport.sbtLaunchJar
import com.typesafe.sbt.packager.Keys.{
  makeBashScript, makeBatScript
}

import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.useNativeZip

import com.typesafe.sbt.license._

package sbt {
  object IvySbtCheater {
    def toID(m: ModuleID) = IvySbt toID m
  }
}


case class LocalRepoReport(location: File, licenses: Seq[License])

object Packaging {
  import com.typesafe.sbt.packager.Keys._
  import com.typesafe.sbt.SbtNativePackager._

  val repackagedLaunchJar = TaskKey[File]("repackaged-launch-jar", "The repackaged launch jar for this product.")
  val repackagedLaunchMappings = TaskKey[Seq[(File, String)]]("repackaged-launch-mappings", "New files for sbt-launch-jar")

  // TODO - rename this to just template directory...
  val scriptTemplateDirectory = SettingKey[File]("script-template-directory")
  val scriptTemplateOutputDirectory = SettingKey[File]("script-template-output-directory")

  val makeReadmeHtml = TaskKey[File]("make-readme-html")
  val makeLicensesHtml = TaskKey[File]("make-licenses-html")

  val localRepoProjectsPublished = TaskKey[Unit]("local-repo-projects-published", "Ensures local projects are published before generating the local repo.")
  val localRepoArtifacts = SettingKey[Seq[ModuleID]]("local-repository-artifacts", "Artifacts included in the local repository.")
  val localRepoName = "install-to-local-repository"
  val localRepo = SettingKey[File]("local-repository", "The location to install a local repository.")
  val localRepoCreation = TaskKey[LocalRepoReport]("local-repository-creation", "Creates a local repository in the specified location.")
  val localRepoLicenses = TaskKey[Unit]("local-repository-licenses", "Prints all the licenses used by software in the local repo.")
  val localRepoCreated = TaskKey[File]("local-repository-created", "Creates a local repository in the specified location.")
  val minimalDist = taskKey[File]("dist without the bundled repository and templates")

  // This is dirty, but play has stolen our keys, and we must mimc them here.
  val stage = TaskKey[File]("stage")
  val dist = TaskKey[File]("dist")

  // Shared settings to make a local repository.
  def makeLocalRepoSettings(lrepoName: String): Seq[Setting[_]] = Seq(
    localRepo <<= target(_ / "local-repository"),
    localRepoArtifacts := Seq.empty,
    resolvers in TheActivatorBuild.dontusemeresolvers <+= localRepo apply { f => Resolver.file(lrepoName, f)(Resolver.ivyStylePatterns) },
    localRepoProjectsPublished <<= (TheActivatorBuild.publishedProjects map (publishLocal in _)).dependOn,
    localRepoCreation <<= (localRepo, localRepoArtifacts, ivySbt in TheActivatorBuild.dontusemeresolvers, streams, localRepoProjectsPublished, target) map { (r, m, i, s, _, t) =>
      val licenses = IvyHelper.createLocalRepository(m, lrepoName, i, t, s.log)
      LocalRepoReport(r, licenses)
    },
    localRepoCreated <<= localRepoCreation map (_.location),
    localRepoLicenses <<= (localRepoCreation, streams) map { (config, s) =>
      // Stylize the licenses we used and give an inline report...
      LicenseReport.dumpReport(LicenseReport(config.licenses, null), println = msg => s.log.info(msg.toString))
    }
  )

  def settings: Seq[Setting[_]] = useNativeZip ++ makeLocalRepoSettings(localRepoName) ++ Seq(
    name in Universal := s"activator-${version.value}",
    wixConfig := <wix/>,
    maintainer := "Josh Suereth <joshua.suereth@typesafe.com>",
    packageSummary := "Activator",
    packageDescription := """Helps developers get started with Typesafe technologies quickly and easily.""",
    stage <<= (target, mappings in Universal) map { (t, m) =>
      val to = t / "stage"
      val copies = m collect { case (f, p) => f -> (to / p) }
      IO.copy(copies)
      // Now set scripts to executable as a hack thanks to Java's lack of understanding of permissions
      (to / "activator").setExecutable(true, true)
      to
    },
    dist <<= packageBin in Universal,
    minimalDist := minimalZip((target in Universal).value, (mappings in Universal).value, version.value),
    mappings in Universal <+= (repackagedLaunchJar, version) map { (jar, v) =>
      jar -> ("activator-launch-%s.jar" format (v))
    },
    mappings in Universal ++= makeBashScript.value.toSeq map (_ -> "activator"),
    mappings in Universal ++= makeBatScript.value.toSeq map (_ -> "activator.bat"),
    mappings in Universal += makeReadmeHtml.value -> "README.html",
    mappings in Universal += makeLicensesHtml.value -> "LICENSE.html",
    mappings in Universal ++= {
      val repo = localRepoCreated.value
      for {
        (file, path) <- (repo.*** --- repo) x relativeTo(repo)
      } yield file -> ("repository/" + path)
    },
    mappings in Universal ++= {
      val repo = (LocalTemplateRepo.localTemplateCacheCreated in TheActivatorBuild.localTemplateRepo).value
      for {
        (file, path) <- (repo.*** --- repo) x relativeTo(repo)
      } yield file -> ("templates/" + path)
    },
    rpmRelease := "1",
    rpmVendor := "typesafe",
    rpmUrl := Some("https://github.com/scala/scala-dist"),
    rpmLicense := Some("BSD"),

    repackagedLaunchJar <<= (target, sbtLaunchJar, repackagedLaunchMappings) map repackageJar,
    repackagedLaunchMappings := Seq.empty,
    repackagedLaunchMappings <+= (target, scalaVersion, version, Properties.launcherGeneration in TheActivatorBuild.props) map makeLauncherProps,

    scriptTemplateDirectory := sourceDirectory.value / "templates",
    scriptTemplateOutputDirectory := (target in Compile).value / "templates",
    makeBashScript := {
      val template = scriptTemplateDirectory.value / "activator"
      val script = scriptTemplateOutputDirectory.value / "activator"
      copyBashTemplate(template, script, version.value)
      Some(script)
    },
    makeBatScript := {
      val template = scriptTemplateDirectory.value / "activator.bat"
      val script = scriptTemplateOutputDirectory.value / "activator.bat"
      copyBatTemplate(template, script, version.value)
      Some(script)
    },
    makeReadmeHtml := {
      val template = scriptTemplateDirectory.value / "README.md"
      val output = scriptTemplateOutputDirectory.value / "README.html"
      Markdown.makeHtml(template, output, title="Activator")
      output
    },
    makeLicensesHtml := {
      val template = scriptTemplateDirectory.value / "LICENSE.md"
      val output = scriptTemplateOutputDirectory.value / "LICENSE.html"
      Markdown.makeHtml(template, output, title="Activator License")
      output
    }
  )


  // TODO - Use SBT caching API for this.
  def repackageJar(target: File, launcher: File, replacements: Seq[(File, String)] = Seq.empty): File = IO.withTemporaryDirectory { tmp =>
    val jardir = tmp / "jar"
    IO.createDirectory(jardir)
    IO.unzip(launcher, jardir)
    // TODO - manually delete sbt.boot.properties for james, since he's seeing wierd issues.
    (jardir ** "sbt.boot.properties0*").get map (f => IO delete f)

    // Copy new files
    val copys =
      for((file, path) <- replacements)
      yield file -> (jardir / path)
    IO.copy(copys, overwrite=true, preserveLastModified=false)

    // Create new launcher jar
    val tmplauncher = tmp / "activator-launcher.jar"
    val files = (jardir.*** --- jardir) x relativeTo(jardir)
    IO.zip(files, tmplauncher)

    // Put new launcher jar in new location.
    val nextlauncher = target / "activator-launcher.jar"
    if(nextlauncher.exists) IO.delete(nextlauncher)
    IO.move(tmplauncher, nextlauncher)
    nextlauncher
  }

  def copyBashTemplate(from: File, to: File, version: String): File = {
    val fileContents = IO read from
    val nextContents = fileContents.replaceAll("""\$\{\{template_declares\}\}""",
                                               """|declare -r app_version="%s"
                                                  |""".stripMargin format (version))
    IO.write(to, nextContents)
    to setExecutable true
    to
  }
  def copyBatTemplate(from: File, to: File, version: String): File = {
    val fileContents = IO read from
    val nextContents = fileContents.replaceAll("""\$\{\{template_declares\}\}""",
                                               "set APP_VERSION=%s" format (version))
    IO.write(to, nextContents)
    to setExecutable true
    to
  }

  // NOTE; Shares boot directory with SBT, good thing or bad?  not sure.
  // TODO - Just put this in the sbt-launch.jar itself!
  def makeLauncherProps(target: File, scalaVersion: String, version: String, launcherGeneration: Int): (File, String) = {
    val tdir = target / "generated-sources"
    if(!tdir.exists) tdir.mkdirs()
    val tprops = tdir / (name + ".properties")
    // TODO - better caching
    // TODO - Add a local repository for resolving...
    // NOTE if someone has ever run Activator before, we'll have created
    // an ~/.sbt/repositories which means the entire [repositories] section
    // below will be ignored. So the repositories section is purely
    // for our first startup.
    if(!tprops.exists) IO.write(tprops, """
[scala]
  version: ${sbt.scala.version-auto}

[app]
  org: com.typesafe.activator
  name: activator-launcher
  version: ${activator.version-read(activator.version)[%s]}
  class: activator.ActivatorLauncher
  cross-versioned: false
  components: xsbti

[repositories]
  local
  activator-launcher-local: file://${activator.local.repository-${activator.home-${user.home}/.activator}/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  maven-central
  typesafe-releases: https://repo.typesafe.com/typesafe/releases
  typesafe-ivy-releasez: https://repo.typesafe.com/typesafe/ivy-releases, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]

[boot]
 directory: ${sbt.boot.directory-${sbt.global.base-${user.home}/.sbt}/boot/}
 properties: ${activator.boot.properties-${user.home}/.activator/version-${activator.launcher.generation-%d}.properties}

[ivy]
  ivy-home: ${user.home}/.ivy2
  checksums: ${sbt.checksums-sha1,md5}
  override-build-repos: ${sbt.override.build.repos-false}
  repository-config: ${sbt.repository.config-${sbt.global.base-${user.home}/.sbt}/repositories}
""" format(version, launcherGeneration))
    tprops -> "sbt/sbt.boot.properties"
  }

  def minimalZip(destDir: File, mappings: Seq[(File, String)], activatorVersion: String): File = {
    val minimalDirName = s"activator-${activatorVersion}-minimal"
    val dest = destDir / s"${minimalDirName}.zip"
    val sourceNames = Seq(s"activator-launch-${activatorVersion}.jar", "activator", "activator.bat")
    val sources = mappings filter {
        case (file, name) => sourceNames.contains(name)
    } map {
        case (file, name) => file -> s"${minimalDirName}/${name}"
    }
    require(sources.length == sourceNames.length)
    com.typesafe.sbt.packager.universal.ZipHelper.zipNative(sources, dest)
    dest
  }
}
