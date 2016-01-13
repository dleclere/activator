/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package activator

import akka.actor._
import sbt.protocol._
import java.io._
import sbt.IO
import scala.concurrent.Future
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits.global

object ProjectPreprocessor {
  final case object Finished

  def noOpPreprocessor(appActor: ActorRef, socket: ActorRef, config: AppConfig): Unit =
    appActor ! Finished

  private final val ossVersionParser = """^(\d+)\.(\d+)\.(\d+)([^"]*)$""".r
  private final val rpVersionParser = """^(\d+)\.(\d+)\.(\d+)([^"]*)-bin-rp-(\d{2})v(\d{2})p(\d{2})$""".r

  private sealed trait ArtifactVersion {
    def versionString: String
    def equalTo(other: ArtifactVersion): Boolean
    def epoch: Int
    def major: Int
    def minor: Int
    def tail: String
    override def toString: String = versionString
  }

  private class OssVersion(val versionString: String) extends ArtifactVersion {
    private final val ossVersionParser(e, m, n, t) = versionString
    def equalTo(other: ArtifactVersion): Boolean = other match {
      case o: OssVersion => versionString.equals(o.versionString)
      case o: TRPVersion => epoch == o.epoch && major == o.major && minor == o.minor
    }

    val epoch: Int = e.toInt
    val major: Int = m.toInt
    val minor: Int = n.toInt
    val tail: String = t
  }

  private class TRPVersion(val versionString: String) extends ArtifactVersion {
    private final val rpVersionParser(e, m, n, t, y, v, p) = versionString
    def equalTo(other: ArtifactVersion): Boolean = other match {
      case o: OssVersion => o.equalTo(this)
      case o: TRPVersion => versionString.equals(o.versionString)
    }
    val epoch: Int = e.toInt
    val major: Int = m.toInt
    val minor: Int = n.toInt
    val tail: String = t
    val year: Int = y.toInt
    val month: Int = v.toInt
    val patch: Int = p.toInt
  }

  private object ArtifactVersion {
    def fromString(versionString: String): ArtifactVersion = versionString match {
      case rpVersionParser(_, _, _, _, _, _, _) => new TRPVersion(versionString)
      case ossVersionParser(_, _, _, _) => new OssVersion(versionString)
    }
  }

  private final class ProjectPreprocessorUtil(appActor: ActorRef, config: AppConfig) {
    def log(level: String, message: String): Unit = appActor ! NotifyWebSocket(SbtProtocol.synthesizeLogEvent(level, message))
    def debug(message: String) = log("debug", message)
    def warn(message: String) = log("warn", message)
    def info(message: String) = log("info", message)
    def error(message: String) = log("error", message)

    def file(in: String) = new File(config.location.getAbsolutePath(), in)
    def withFile[T](in: String)(body: File => T): Option[T] = {
      val f = file(in)
      if (f.exists()) Some(body(f))
      else None
    }
    def exists(in: String) = {
      val f = file(in)
      f.exists()
    }
    def delete(in: String): Unit = {
      if (file(in).delete()) debug(s"deleting $in -> success")
      else debug(s"deleting $in -> FAILURE!!")
    }
    def readFile(in: String): Option[String] = withFile(in)(f => IO.read(f))
    def writeFile(inf: String, contents: String): Unit = {
      val f = file(inf)
      IO.write(f, contents)
    }

  }

  private final val targetPlay23Version = "2.3.10"
  private final val playPluginVersionRegex = """\d+\.\d+\.\d+[^"]*"""
  private final val playPluginVersionPattern = playPluginVersionRegex.r
  private final val playPluginPattern = (""""com\.typesafe\.play"\s*%\s*"sbt-plugin"\s*%\s*"""" + playPluginVersionRegex + "\"").r
  private final val oldPlayPluginPattern = (""""play"\s*%\s*"sbt-plugin"\s*%\s*"""" + playPluginVersionRegex + "\"").r
  private final def playPluginReplacementString(version: String): String = s""""com.typesafe.play" % "sbt-plugin" % "$version""""
  private final val playForkRunPluginPattern = (""""com\.typesafe\.play"\s*%\s*"sbt-fork-run-plugin"\s*%\s*"""" + playPluginVersionRegex + "\"").r

  private final def readPlugins(util: ProjectPreprocessorUtil): Option[String] = {
    util.info("Examining project/plugins.sbt")
    util.readFile("project/plugins.sbt")
  }
  private final def readPlay(util: ProjectPreprocessorUtil): Option[String] = {
    util.info("Examining project/play.sbt")
    util.readFile("project/play.sbt")
  }
  private final def readPlayForkRunPlugin(util: ProjectPreprocessorUtil): Option[String] = {
    util.info("Examining project/play-fork-run.sbt")
    util.readFile("project/play-fork-run.sbt")
  }

  private final def getPlayVersion(source: String): Option[ArtifactVersion] = for {
    playPlugin <- playPluginPattern.findFirstIn(source) orElse oldPlayPluginPattern.findFirstIn(source)
    version <- playPluginVersionPattern.findFirstIn(playPlugin)
  } yield ArtifactVersion.fromString(version)

  private final def getPlayForkRunnerVersion(source: String): Option[ArtifactVersion] = for {
    playForkRunnerPlugin <- playForkRunPluginPattern.findFirstIn(source)
    version <- playPluginVersionPattern.findFirstIn(playForkRunnerPlugin)
  } yield ArtifactVersion.fromString(version)

  private final def rewritePlayPlugin(util: ProjectPreprocessorUtil, version: String): Unit = {
    util.info(s"Rewriting Play plugin version to $version")
    val nv = playPluginReplacementString(version)
    val p1 = for {
      source <- readPlay(util)
      _ <- getPlayVersion(source)
    } yield playPluginPattern.replaceAllIn(source, nv)
    val p2 = for {
      source <- readPlugins(util)
      _ <- getPlayVersion(source)
    } yield playPluginPattern.replaceAllIn(source, nv)
    p1.foreach(util.writeFile("project/play.sbt", _))
    p2.foreach(util.writeFile("project/plugins.sbt", _))
  }

  private final def examinePlay(util: ProjectPreprocessorUtil): Option[ArtifactVersion] = for {
    source <- readPlay(util) orElse readPlugins(util)
    version <- getPlayVersion(source)
  } yield version

  private final def examinePlayForkRunner(util: ProjectPreprocessorUtil): Option[ArtifactVersion] = for {
    source <- readPlayForkRunPlugin(util)
    version <- getPlayForkRunnerVersion(source)
  } yield version

  def defaultPreprocessor(appActor: ActorRef, socket: ActorRef, config: AppConfig): Unit = {
    Future {
      val util = new ProjectPreprocessorUtil(appActor: ActorRef, config: AppConfig)
      val playVersion = examinePlay(util)
      val playForkRunnerVersion = examinePlayForkRunner(util)
      (playVersion, playForkRunnerVersion) match {
        case (None, None) =>
          util.info("Does not appear to be a Play project")
        case (None, Some(_)) =>
          util.warn("Possible Play project, but cannot detect play version")
        case (Some(pv), pfrv) if pv.epoch == 2 =>
          pv.major match {
            case 4 => util.info(s"Using Play $pv -> OK")
            case 3 if pv.minor >= 8 => util.info(s"Using Play $pv -> OK")
            case 3 if pv.minor < 8 =>
              util.warn(s"Using Play < 2.3.8 -> will update to $targetPlay23Version")
              rewritePlayPlugin(util, targetPlay23Version)
              util.delete("project/play-fork-run.sbt")
            case _ =>
              util.error(s"Using unsupported version of Play: $pv")
              util.delete("project/play-fork-run.sbt")
          }

          pfrv.foreach { v =>
            if (!v.equalTo(pv)) {
              util.info("Fork-runner mispatch ... rebuilding")
              util.delete("project/play-fork-run.sbt")
            }
          }
      }
    } onComplete (_ => appActor ! Finished)
  }

}
