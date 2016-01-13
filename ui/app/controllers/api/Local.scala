/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package controllers.api

import play.api.mvc.{ Action, Controller }
import play.api.libs.json._
import java.io.File
import activator.Platform
import play.api.data._
import play.api.data.Forms._
import play.api.Logger
import scala.util.control.NonFatal

object Local extends Controller {

  def getEnv = Action { request =>
    val localEnvJson = Json.toJson(
      Map(
        "desktopDir" ->
          Json.toJson(Platform.getClientFriendlyFilename(new File(System.getProperty("user.home"), "Desktop"))),
        "separator" -> Json.toJson(File.separator)))

    Ok(localEnvJson)
  }

  def getExtension(name: String): String =
    (for {
      n <- Option(name)
      idx = n lastIndexOf '.'
      if idx != -1
      sub = n substring idx
    } yield (sub drop 1)) getOrElse ""

  // TODO - Make this configurable!
  def getFileType(file: File): String = getExtension(file.getName) match {
    case "html" => "code"
    case "scala" => "code"
    case "java" => "code"
    case "jpg" => "image"
    case "js" => "code"
    case "gif" => "image"
    case "png" => "image"
    case "sbt" => "code"
    case "coffee" => "code"
    case "conf" => "code"
    case "css" => "code"
    case "less" => "code"
    case "text" => "code"
    case "md" => "code"
    case "rst" => "code"
    case "properties" => "code"
    case "bat" => "code"
    case "xml" => "code"
    case "json" => "code"
    case "plugins" => "code"
    case "sql" => "code"
    // TODO - New "tail" viewer for logs?
    case "log" => "code"
    case "txt" => "code"
    // If we can't find any specific handler based on extension, certain files still need
    // to be handled by name....
    case _ => file.getName match {
      case "routes" => "code"
      case _ => detectTypeFromMime(file)
    }
  }

  def detectTypeFromMime(f: File): String = {
    val mime = getMimeType(f)
    Logger.debug(s"Checking mime type for $f, found: $mime");
    if (mime startsWith "text") "code"
    else if (mime startsWith "image") "image"
    else if (mime contains "x-shellscript") "code" // TODO - highlighting is still borked for this guy
    else if (canDecodeAsText(f)) "code"
    else "binary"
  }

  final val mimeDetector = "eu.medsea.mimeutil.detector.MagicMimeMimeDetector"

  // Magically discover the mime type! synchronize all our use of this
  // library in case that's making it flaky (it seems to throw internal
  // NPEs sometimes)
  def getMimeType(file: File): String = synchronized {
    import eu.medsea.mimeutil._
    if (MimeUtil.getMimeDetector(mimeDetector) == null) {
      MimeUtil.registerMimeDetector(mimeDetector);
    }
    val mimeTypes = MimeUtil getMimeTypes file
    MimeUtil.getFirstMimeType(mimeTypes.toString).toString
  }

  // avoid reading giant files
  private final val MAX_FILE_SIZE_TO_TRY_DECODING = 50 * 1024 * 1024;

  // this is expensive as heck but we skip it for known extensions so
  // hopefully it isn't a big deal...
  def canDecodeAsText(file: File): Boolean = {
    if (file.length > MAX_FILE_SIZE_TO_TRY_DECODING) {
      Logger.debug("File too large to analyze whether it's a UTF-8 file: " + file.getName)
      false
    } else try {
      import java.io._
      import java.nio.charset._

      val reader = new InputStreamReader(new FileInputStream(file),
        Charset.forName("UTF-8").newDecoder.onMalformedInput(CodingErrorAction.REPORT))
      reader.skip(MAX_FILE_SIZE_TO_TRY_DECODING)
      reader.close()
      Logger.debug("Determined that file is UTF-8: " + file.getName)
      true
    } catch {
      case NonFatal(e) =>
        Logger.debug(s"Determined that file is not UTF-8 or not readable: ${e.getClass.getName}: ${e.getMessage}")
        false
    }
  }

  // Here's the JSON rendering of template metadata.
  implicit object FileProtocol extends Format[File] {
    def writes(o: File): JsValue =
      JsObject(
        List("name" -> JsString(o.getName),
          "location" -> JsString(Platform.getClientFriendlyFilename(o)),
          "humanLocation" -> JsString(o.getAbsolutePath),
          "isRoot" -> JsBoolean(File.listRoots.exists(f => o.getCanonicalPath == f.getCanonicalPath)),
          "isDirectory" -> JsBoolean(o.isDirectory)) ++
          (if (o.isDirectory) Nil
          else List(
            "type" -> JsString(getFileType(o)),
            "size" -> JsNumber(o.length))))
    //We don't need reads, really
    def reads(json: JsValue): JsResult[File] =
      JsError("Reading TemplateMetadata not supported!")
  }
  case class InterestingFile(file: File)
  implicit object IFileProtocol extends Format[InterestingFile] {
    def writes(o: InterestingFile): JsValue =
      if (o.file.isDirectory) JsObject(List(
        "name" -> JsString(o.file.getName),
        "location" -> JsString(Platform.getClientFriendlyFilename(o.file)),
        "humanLocation" -> JsString(o.file.getAbsolutePath),
        "isDirectory" -> JsBoolean(true),
        "type" -> JsString("directory"),
        // TODO - Only if parent file exists!
        "isRoot" -> JsBoolean(File.listRoots.exists(f => o.file.getCanonicalPath == f.getCanonicalPath)),
        // TODO - Gitignore/file filters here.
        "children" -> Json.toJson(o.file.listFiles().filterNot(_.getName startsWith "."))) ++
        Option(o.file.getParentFile).map(f => "parent" -> Json.toJson(f)).toList)
      else FileProtocol.writes(o.file)
    //We don't need reads, really
    def reads(json: JsValue): JsResult[InterestingFile] =
      JsError("Reading Files not supported!")
  }

  def browse(location: String) = Action { request =>
    val loc = Platform.fromClientFriendlyFilename(location)
    if (!loc.exists) NotAcceptable(s"${location} is not a file!")
    else if (!canAccess(loc)) NotAcceptable(s"${location} is not readable!")
    else Ok(Json toJson InterestingFile(loc))
  }

  def browseRoots = Action { request =>
    val roots = File.listRoots.toList
    Ok(Json toJson roots)
  }

  def show(location: String) = Action { request =>
    val loc = Platform.fromClientFriendlyFilename(location)
    if (!loc.exists) NotAcceptable(s"${location} is not a file!")
    else (Ok sendFile loc)
  }

  // Open with a local file browser
  def open(location: String) = Action { request =>
    val loc = Platform.fromClientFriendlyFilename(location)
    val desktop = java.awt.Desktop.getDesktop
    if (!loc.exists) NotAcceptable(s"${location} is not a file or directory!")
    else if (!desktop.isSupported(java.awt.Desktop.Action.OPEN)) NotAcceptable("Opening a file browser is unsupported on your machine.")
    else if (!canAccess(loc)) NotAcceptable(s"${location} is not readable!")
    else try {
      desktop open loc
      Ok("")
    } catch {
      case ex: java.io.IOException =>
        NotAcceptable(s"Failed to open: ${location}.  Is there a program associated with the extension?")
    }
  }

  val saveFileForm = Form(tuple("location" -> text, "content" -> text))
  def save = Action { implicit request =>
    // TODO - use Validation here.
    val (location, content) = saveFileForm.bindFromRequest.get
    val loc = Platform.fromClientFriendlyFilename(location)
    // We should probably just save any file...
    import sbt.IO
    IO.withTemporaryFile("activator", "save-file") { file =>
      IO.write(file, content)
      IO.move(file, loc)
    }
    Ok(content)
  }

  val createFileForm = Form(tuple("location" -> text, "isDirectory" -> boolean, "content" -> text))
  def createFile = Action { implicit request =>
    val (location, isDirectory, content) = createFileForm.bindFromRequest.get
    val loc = Platform.fromClientFriendlyFilename(location)
    try {
      import sbt.IO
      if (isDirectory)
        IO.createDirectory(loc)
      else
        IO.write(loc, content)
      Logger.debug(s"created $loc OK")
      Ok("")
    } catch {
      case NonFatal(e) =>
        Logger.debug(s"failed to create $loc: ${e.getClass.getName}: ${e.getMessage}")
        NotAcceptable(s"failed to create: $loc: ${e.getMessage}")
    }
  }

  val renameForm = Form(tuple("location" -> text, "newName" -> text))
  def renameFile = Action { implicit request =>
    val (location, newName) = renameForm.bindFromRequest.get
    val loc = Platform.fromClientFriendlyFilename(location)
    val newLoc = new File(loc.getParentFile(), newName)
    try {
      import sbt.IO
      IO.move(loc, newLoc)
      Logger.debug(s"successful rename $loc to $newLoc")
      Ok("")
    } catch {
      case NonFatal(e) =>
        Logger.debug(s"failed to rename $loc to $newLoc: ${e.getClass.getName}: ${e.getMessage}")
        NotAcceptable(s"failed to rename: $loc to $newLoc: ${e.getMessage}")
    }
  }

  val deleteForm = Form("location" -> text)
  def deleteFile = Action { implicit request =>
    val location = deleteForm.bindFromRequest.get
    val loc = Platform.fromClientFriendlyFilename(location)
    try {
      import sbt.IO
      IO.delete(loc)
      Logger.debug(s"successful delete of $loc")
      Ok("")
    } catch {
      case NonFatal(e) =>
        Logger.debug(s"failed to delete $loc: ${e.getClass.getName}: ${e.getMessage}")
        NotAcceptable(s"failed to delete '$loc': ${e.getMessage}")
    }
  }

  /**
   * To prevent NPE in Windows when trying to list directories that are so called "junction points".
   * In case of such a directory File.listFiles will return null and since we have to use Java 6
   * we cannot use java.nio.* to handle this in a nicer way.
   */
  private def canAccess(file: File): Boolean = if (!file.isDirectory) true else file.listFiles != null

}
