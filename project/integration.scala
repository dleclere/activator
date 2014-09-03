import sbt._
import Keys._
import SbtSupport.sbtLaunchJar
import Packaging.{
  localRepoArtifacts,
  localRepoCreated,
  makeLocalRepoSettings,
  localRepoProjectsPublished,
  repackagedLaunchJar
}
import xsbt.api.Discovery
import com.typesafe.sbt.SbtNativePackager.Universal

case class IntegrationTestResult(name: String, passed: Boolean, log: File)

object integration {
  
  val mains = TaskKey[Seq[String]]("integration-test-mains", "Discovered integration test main classes")
  val itContext = TaskKey[IntegrationContext]("integration-test-context")
  val integrationTestsWithoutOffline = taskKey[Unit]("Runs all integration tests without the offline tests")
  val tests = TaskKey[Unit]("integration-tests", "Runs all integration tests")
  val singleTest = InputKey[Seq[IntegrationTestResult]]("integration-test-only", "Runs integration tests that match the given glob")
  val integrationHome = TaskKey[File]("integration-home", "Creates the home directory for use in integration tests.")
  
  def settings: Seq[Setting[_]] = makeLocalRepoSettings("install-to-it-repository") ++ Seq(
    localRepoArtifacts := Seq.empty,
    // Make sure we publish this project.
    localRepoProjectsPublished <<= publishLocal,
    mains <<= compile in Compile map { a =>
      val defs = a.apis.internal.values.flatMap(_.api.definitions)
      val results = Discovery(Set("xsbti.Main"), Set())(defs.toSeq)
      results collect { 
        case (df, di) if !di.isModule && !df.modifiers.isAbstract => df.name
      }
    },
    itContext <<= (sbtLaunchJar, localRepoCreated, streams, version, target, scalaVersion, integrationHome) map IntegrationContext.apply,
    integrationTestsWithoutOffline <<= (itContext, mains, streams) map { (ctx, ms, s) =>
      val results = ms map ctx.runTest
      handleResults(results, s)
    },
    tests := integrationTestsWithoutOffline.value, // offline adds offline to this
    localRepoArtifacts <+= (Keys.projectID, Keys.scalaBinaryVersion, Keys.scalaVersion) apply {
      (id, sbv, sv) => CrossVersion(sbv,sv)(id)
    },
    integrationHome <<= (target, mappings in Universal in TheActivatorBuild.dist) map { (t, m) =>
       val home = t / "integrationHome-home"
       IO createDirectory home
       val homeFiles = for {
         (file, name) <- m
       } yield file -> (home / name)
       IO.copy(homeFiles)
       home
    },
    singleTest <<= inputTask { argTask =>
      (argTask, itContext, mains, streams) map { (args, ctx, mains, s ) =>
        val glob = args mkString " "
        val results = mains filter (_ contains glob) map ctx.runTest
        handleResults(results, s)
        results
      }
    }
  )
  
  def handleResults(results: Seq[IntegrationTestResult], out: TaskStreams): Unit = {
    // TODO - Only colorize if we're in ANSI terminal.
    out.log.info(scala.Console.BLUE + " --- Integration Test Report ---" + scala.Console.BLUE_B)
    val maxName = results.map(_.name.length).max
    def padName(name: String): String = {
      val pad = Stream.continually(' ').take(maxName - name.length).mkString("")
      pad + name
    }
    for(result <- results.sortBy(r => r.passed + r.name)) {
      val resultString =
        if(result.passed) "[ " + scala.Console.GREEN + "PASSED"+ scala.Console.RESET +" ]"
        else              "[ " + scala.Console.RED + "FAILED"+ scala.Console.RESET +" ]"
      val seeString =
        if(result.passed) ""
        else (" see " + result.log.getAbsolutePath+scala.Console.RESET)
      out.log.info(" * " + padName(result.name) + " " + resultString + seeString)
    }
    if(results.exists(!_.passed)) {
      sys.error("Failing integration tests!")
    }
  }
}


case class IntegrationContext(launchJar: File, 
                               repository: File,
                               streams: TaskStreams,
                               version: String,
                               target: File,
                               scalaVersion: String,
                               integrationHome: File) {

  def runTest(name: String): IntegrationTestResult = {
    streams.log.info(scala.Console.BLUE+" [IT] Running: " + name + " [IT]"+scala.Console.BLUE_B)
    val friendlyName = name replaceAll("\\.", "-")
    val cwd = target / "integration-test" / friendlyName
    val logFile = target / "integration-test" / (friendlyName + ".log")
    sbt.IO.touch(logFile)
    val fileLogger = ConsoleLogger(new java.io.PrintWriter(new java.io.FileWriter(logFile)))
    // TODO - Filter logs so we're not so chatty on the console.
    val logger = new MultiLogger(List(fileLogger, streams.log.asInstanceOf[AbstractLogger]))
    // First clean the old test....
    IO delete cwd
    IO createDirectory cwd
    // Here, let's create a new logger that can store logs in a location of our choosing too...
    setup(name, cwd) ! logger match {
      case 0 => 
        streams.log.info(" [IT] " + name + " result: SUCCESS")
        IntegrationTestResult(name, true, logFile)
      case n => 
        streams.log.error(" [IT] " + name + " result: FAILURE")
        IntegrationTestResult(name, false, logFile)
    }
  }
  
  
  private def cleanUriFileString(file: String): String =
	  file.replaceAll(" ", "%20")
  private def setup(name: String, cwd: File): ProcessBuilder = {
    val props = cwd / "sbt.boot.properties"
    IO.write(props, makePropertiesString(name, cwd))
    IO createDirectory (cwd / "project")
    IO.write(cwd / "project" / "build.properties", "sbt.version=" + Dependencies.sbtVersion)
    val boot = cwd / "boot"
    Process(Seq("java", 
        "-Dsbt.boot.properties=" + props.getAbsolutePath, 
        "-Dsbt.boot.directory=" + boot.getAbsolutePath,
        "-Dactivator.integration.playVersion=" + Dependencies.playVersion,
        "-Dactivator.home=" +cleanUriFileString(integrationHome.getAbsolutePath),
        "-jar", 
        launchJar.getAbsolutePath), cwd)
  }
  
  
  private def makePropertiesString(name: String, cwd: File): String =
    """|[scala]
       |  version: ${sbt.scala.version-auto}
       |
       |[app]
       |  org: com.typesafe.activator
       |  name: activator-integration-tests
       |  version: %s
       |  class: %s
       |  cross-versioned: false
       |  components: xsbti
       |
       |[repositories]
       |  activator-local: file://${activator.local.repository-${activator.home-${user.home}/.activator}/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
       |  activator-it-local: file://%s, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
       |
       |[boot]
       |  directory: ${sbt.boot.directory}
       |
       |[ivy]
       |  ivy-home: %s/.ivy2
       |  checksums: ${sbt.checksums-sha1,md5}
       |  override-build-repos: ${sbt.override.build.repos-false}
       |""".stripMargin format (version, name, cleanUriFileString(repository.getAbsolutePath), cleanUriFileString(cwd.getAbsolutePath), cwd.getAbsolutePath)
}
