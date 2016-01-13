/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package activator

import play.api.libs.json._
import scala.concurrent._
import ExecutionContext.Implicits.global
import java.io._
import activator.properties.ActivatorProperties.ACTIVATOR_USER_CONFIG_FILE
import activator.properties.ActivatorProperties.ACTIVATOR_PREVIOUS_USER_CONFIG_FILE
import activator.properties.ActivatorProperties.TEMPLATE_UUID_PROPERTY_NAME
import scala.concurrent.duration._
import sbt.IO

// createdTime and usedTime are only optional due to legacy config files
case class AppConfig(location: File, id: String, createdTime: Option[Long], usedTime: Option[Long], cachedName: Option[String] = None) {
  // TODO - this method is dangerous, as it hits the file system.
  // Figure out when it should initialize/run.
  val templateID: Option[String] =
    try {
      val props = new java.util.Properties
      sbt.IO.load(props, new java.io.File(location, "project/build.properties"))
      Option(props.getProperty(TEMPLATE_UUID_PROPERTY_NAME, null))
    } catch {
      case e: java.io.IOException => None // TODO - Log?
    }
}

object AppConfig {
  import play.api.data.validation.ValidationError

  implicit object FileWrites extends Writes[File] {
    def writes(file: File) = JsString(file.getPath)
  }

  implicit val writes = Json.writes[AppConfig]

  implicit object FileReads extends Reads[File] {
    def reads(json: JsValue) = json match {
      case JsString(path) => JsSuccess(new File(path))
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("validate.error.expected.jsstring"))))
    }
  }

  implicit val reads = Json.reads[AppConfig]
}

case class RootConfig(applications: Seq[AppConfig])

trait RootConfigOps {
  protected def userConfigFile: File
  protected def previousUserConfigFile: File

  private def loadUser = ConfigFile(userConfigFile, upgradeFrom = Some(previousUserConfigFile))

  // volatile because we read it unsynchronized. we don't care
  // which one we get, just something sane. Also double-checked
  // locking below requires volatile.
  // this is an Option so we can make forceReload() defer reloading
  // by setting to None and then going back to Some "on demand"
  @volatile private var userFutureOption: Option[Future[ConfigFile]] = None

  def forceReload(): Unit = {
    // we want to ensure we reload the file next time, but
    // avoid kicking off the reload now since we probably JUST
    // discovered the file was broken.
    userFutureOption = None
  }

  // get the current per-user configuration
  def user: RootConfig = try {
    // double-checked locking
    val userFuture = userFutureOption match {
      case None => synchronized {
        if (userFutureOption.isEmpty)
          userFutureOption = Some(loadUser)
        userFutureOption.get
      }
      case Some(f) => f
    }
    // we use the evil Await because 99% of the time we expect
    // the Future to be completed already.
    Await.result(userFuture.map(_.config), 8.seconds)
  } catch {
    case e: Exception =>
      // retry next time
      forceReload()
      // but go ahead and throw this time
      throw e
  }

  // modify the per-user configuration
  def rewriteUser(f: RootConfig => RootConfig): Future[Unit] = {
    // the "synchronized" is intended to ensure that all "f"
    // transformations in fact take place, though in undefined
    // order. Otherwise we could use the same future twice as
    // the "old" and generate two "new" one of which would be
    // discarded.
    synchronized {
      // note that the actual file-rewriting is NOT synchronized,
      // it is async. We're just synchronizing storing the Future
      // in our var so that no Future is "skipped"
      val userFuture = userFutureOption.getOrElse(loadUser) flatMap { configFile =>
        ConfigFile.rewrite(configFile)(f)
      }
      userFutureOption = Some(userFuture)
      userFuture map { _ => () }
    }
  }
}

object RootConfig extends RootConfigOps {
  implicit val writes = Json.writes[RootConfig]
  implicit val reads = Json.reads[RootConfig]

  // has to be lazy because trait uses it to init
  override lazy val userConfigFile = (new File(ACTIVATOR_USER_CONFIG_FILE)).getCanonicalFile()
  override lazy val previousUserConfigFile = (new File(ACTIVATOR_PREVIOUS_USER_CONFIG_FILE)).getCanonicalFile()
}

private[activator] class ConfigFile(val file: File, json: JsValue) {
  require(file ne null)
  require(file.getParentFile ne null)
  val config = json.as[RootConfig]
}

private[activator] object ConfigFile {
  private def parse(file: File, upgradeFrom: Option[File]): JsValue = try {
    val input = new FileInputStream(file)
    val s = try {
      val out = new ByteArrayOutputStream()
      copy(input, out)
      new String(out.toString("UTF-8"))
    } finally {
      input.close()
    }
    Json.parse(s) match {
      case x: JsObject => x
      case whatever => throw new Exception("config file contains non-JSON-object")
    }
  } catch {
    case e: FileNotFoundException =>
      upgradeFrom map { old =>
        parse(old, upgradeFrom = None)
      } getOrElse {
        Json.toJson(RootConfig(Seq.empty[AppConfig]))
      }
  }

  def apply(file: File, upgradeFrom: Option[File]): Future[ConfigFile] = {
    // a file that hasn't been "canonicalized" may not have
    // a parent file which eventually leads to NPE.
    val canonicalFile = file.getCanonicalFile
    require(canonicalFile.getParentFile ne null)
    Future {
      val obj = parse(canonicalFile, upgradeFrom)
      new ConfigFile(canonicalFile, obj)
    } flatMap { cf =>
      if (cf.file.exists) {
        Future.successful(cf)
      } else {
        // we must have upgraded, be sure to write
        // out the new file in the new location
        rewrite(cf)(identity)
      }
    }
  }

  def rewrite(configFile: ConfigFile)(f: RootConfig => RootConfig): Future[ConfigFile] = {
    val newJson = Json.toJson(f(configFile.config))

    Future {
      // we parse the json we create back before doing any IO, as a sanity check
      val newConfig = new ConfigFile(configFile.file, newJson)

      val tmpFile = new File(newConfig.file.getCanonicalPath + ".tmp")
      ignoringIOException { IO.createDirectory(tmpFile.getParentFile) }
      ignoringIOException { IO.delete(tmpFile) }
      val bytesToWrite = newJson.toString.getBytes("UTF-8")
      val out = new FileOutputStream(tmpFile)
      try {
        val in = new ByteArrayInputStream(bytesToWrite)
        copy(in, out)
      } finally {
        out.close()
      }
      // kind of a silly paranoia check
      if (tmpFile.length() != bytesToWrite.length)
        throw new IOException("File does not have expected size: " + tmpFile.getCanonicalPath() + ": " + bytesToWrite.length)
      // then copy over
      IO.move(tmpFile, newConfig.file)

      newConfig
    }
  }

  private def ignoringIOException[T](block: => T): Unit = {
    try {
      block
    } catch {
      case e: IOException => ()
    }
  }

  private val MAX_BUF = 1024 * 1024
  private val MIN_BUF = 1024

  private def copy(in: InputStream, out: OutputStream): Long = {
    val buf = new Array[Byte](Math.min(MAX_BUF, Math.max(MIN_BUF, in.available())))
    var bytesWritten = 0
    var bytesRead = 0
    bytesRead = in.read(buf)
    while (bytesRead != -1) {
      out.write(buf, 0, bytesRead)
      bytesWritten += bytesRead
      bytesRead = in.read(buf)
    }
    bytesWritten
  }
}
