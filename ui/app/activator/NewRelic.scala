/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package activator

import java.io.File

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Json._

object NewRelicRequest {
  import RequestHelpers._
  import JsonHelper._

  val requestTag = "NewRelicRequest"
  val responseTag = "monitoring"
  val responseSubTag = "newrelic"

  sealed trait Request {
    def error(message: String): Response =
      ErrorResponse(message, this)
  }
  case object Provision extends Request {
    def response: Response = Provisioned
  }
  case object Available extends Request {
    def response(result: Boolean): Response = AvailableResponse(result, this)
  }
  case class EnableProject(key: String, appName: String) extends Request {
    def response: Response = ProjectEnabled(this)
  }
  case object IsProjectEnabled extends Request {
    def response(result: Boolean): Response = IsProjectEnabledResponse(result, this)
  }
  case object Deprovision extends Request {
    def response: Response = Deprovisioned
  }
  case object IsSupportedJavaVersion extends Request {
    def response(result: Boolean, version: String): Response = IsSupportedJavaVersionResult(result, version, this)
  }
  case class GenerateFiles(location: String, root: File) extends Request {
    def response: Response = GeneratedFiles(this)
  }

  sealed trait Response {
    def request: Request
  }
  case object Provisioned extends Response {
    final val request: Request = Provision
  }
  case object Deprovisioned extends Response {
    val request: Request = Deprovision
  }
  case class ErrorResponse(message: String, request: Request) extends Response
  case class AvailableResponse(result: Boolean, request: Request) extends Response
  case class ProjectEnabled(request: Request) extends Response
  case class IsProjectEnabledResponse(result: Boolean, request: Request) extends Response
  case class IsSupportedJavaVersionResult(result: Boolean, version: String, request: Request) extends Response
  case class GeneratedFiles(request: Request) extends Response

  implicit val newRelicProvisionReads: Reads[Provision.type] =
    extractRequest[Provision.type](requestTag)(extractTypeOnly("provision", Provision))

  implicit val newRelicDeprovisionReads: Reads[Deprovision.type] =
    extractRequest[Deprovision.type](requestTag)(extractTypeOnly("deprovision", Deprovision))

  implicit val newRelicDeprovisionWrites: Writes[Deprovision.type] =
    emitRequest(requestTag)(_ => Json.obj("type" -> "deprovision"))

  implicit val newRelicIsProjectEnabledReads: Reads[IsProjectEnabled.type] =
    extractRequest[IsProjectEnabled.type](requestTag)(extractTypeOnly("isProjectEnabled", IsProjectEnabled))

  implicit val newRelicIsSupportedJavaVersionReads: Reads[IsSupportedJavaVersion.type] =
    extractRequest[IsSupportedJavaVersion.type](requestTag)(extractTypeOnly("isSupportedJavaVersion", IsSupportedJavaVersion))

  implicit val newRelicProvisionWrites: Writes[Provision.type] =
    emitRequest(requestTag)(_ => Json.obj("type" -> "provision"))

  implicit val newRelicIsProjectEnabledWrites: Writes[IsProjectEnabled.type] =
    emitRequest(requestTag)(_ => Json.obj("type" -> "isProjectEnabled"))

  implicit val newRelicIsSupportedJavaVersionWrites: Writes[IsSupportedJavaVersion.type] =
    emitRequest(requestTag)(_ => Json.obj("type" -> "isSupportedJavaVersion"))

  implicit val newRelicAvailableReads: Reads[Available.type] =
    extractRequest[Available.type](requestTag)(extractTypeOnly("available", Available))

  implicit val newRelicAvailableWrites: Writes[Available.type] =
    emitRequest(requestTag)(_ => Json.obj("type" -> "available"))

  implicit val newRelicEnableProjectReads: Reads[EnableProject] =
    extractRequest[EnableProject](requestTag)(extractType("enable")(((__ \ "key").read[String] and
      (__ \ "name").read[String])(EnableProject.apply _)))

  implicit val newRelicEnableProjectWrites: Writes[EnableProject] =
    emitRequest(requestTag)(in => Json.obj("type" -> "enable",
      "key" -> in.key,
      "name" -> in.appName))

  implicit val newRelicGenerateFilesReads: Reads[GenerateFiles] =
    extractRequest[GenerateFiles](requestTag)(extractType("generateFiles")(((__ \ "location").read[String] and
      (__ \ "info").read[File])(GenerateFiles.apply _)))

  implicit val newRelicGenerateFilesWrites: Writes[GenerateFiles] =
    emitRequest(requestTag)(in => Json.obj("type" -> "generateFiles", "location" -> in.location))

  implicit val newRelicRequestReads: Reads[Request] = {
    val pr = newRelicProvisionReads.asInstanceOf[Reads[Request]]
    val ar = newRelicAvailableReads.asInstanceOf[Reads[Request]]
    val epr = newRelicEnableProjectReads.asInstanceOf[Reads[Request]]
    val iper = newRelicIsProjectEnabledReads.asInstanceOf[Reads[Request]]
    val de = newRelicDeprovisionReads.asInstanceOf[Reads[Request]]
    val ijs = newRelicIsSupportedJavaVersionReads.asInstanceOf[Reads[Request]]
    val gf = newRelicGenerateFilesReads.asInstanceOf[Reads[Request]]
    extractRequest[Request](requestTag)(pr.orElse(ar).orElse(epr).orElse(iper).orElse(de).orElse(ijs).orElse(gf))
  }

  implicit val newRelicProvisionedWrites: Writes[Provisioned.type] =
    emitResponse(responseTag, responseSubTag)(in => Json.obj("event" ->
      Json.obj(
        "type" -> "provisioned",
        "request" -> in.request)))

  implicit val newRelicDeprovisionedWrites: Writes[Deprovisioned.type] =
    emitResponse(responseTag, responseSubTag)(in => Json.obj("event" ->
      Json.obj(
        "type" -> "deprovisioned",
        "request" -> in.request)))

  implicit val newRelicIsProjectEnabledResponseWrites: Writes[IsProjectEnabledResponse] =
    emitResponse(responseTag, responseSubTag)(in => Json.obj("event" ->
      Json.obj(
        "type" -> "isProjectEnabledResponse",
        "result" -> in.result,
        "request" -> in.request)))

  implicit val newRelicIsSupportedJavaVersionResultWrites: Writes[IsSupportedJavaVersionResult] =
    emitResponse(responseTag, responseSubTag)(in => Json.obj("event" ->
      Json.obj(
        "type" -> "isSupportedJavaVersionResult",
        "result" -> in.result,
        "version" -> in.version,
        "request" -> in.request)))

  implicit val newRelicAvailableResponseWrites: Writes[AvailableResponse] =
    emitResponse(responseTag, responseSubTag)(in => Json.obj("event" ->
      Json.obj(
        "type" -> "availableResponse",
        "result" -> in.result,
        "request" -> in.request)))

  implicit val newRelicProjectEnabledWrites: Writes[ProjectEnabled] =
    emitResponse(responseTag, responseSubTag)(in => Json.obj("event" ->
      Json.obj(
        "type" -> "projectEnabled",
        "request" -> in.request)))

  implicit val newRelicErrorResponseWrites: Writes[ErrorResponse] =
    emitResponse(responseTag, responseSubTag)(in => Json.obj("event" ->
      Json.obj(
        "type" -> "error",
        "message" -> in.message,
        "request" -> in.request)))

  implicit val newRelicGeneratedFilesWrites: Writes[GeneratedFiles] =
    emitResponse(responseTag, responseSubTag)(in => Json.obj("event" ->
      Json.obj(
        "type" -> "generatedFiles",
        "request" -> in.request)))

  implicit val newRelicRequestWrites: Writes[Request] =
    Writes {
      case x: EnableProject => newRelicEnableProjectWrites.writes(x)
      case x @ IsProjectEnabled => newRelicIsProjectEnabledWrites.writes(x)
      case x @ Provision => newRelicProvisionWrites.writes(x)
      case x @ Available => newRelicAvailableWrites.writes(x)
      case x @ Deprovision => newRelicDeprovisionWrites.writes(x)
      case x @ IsSupportedJavaVersion => newRelicIsSupportedJavaVersionWrites.writes(x)
      case x: GenerateFiles => newRelicGenerateFilesWrites.writes(x)
    }

  implicit val newRelicResponseWrites: Writes[Response] =
    Writes {
      case x @ Provisioned => newRelicProvisionedWrites.writes(x)
      case x: IsProjectEnabledResponse => newRelicIsProjectEnabledResponseWrites.writes(x)
      case x: AvailableResponse => newRelicAvailableResponseWrites.writes(x)
      case x: ProjectEnabled => newRelicProjectEnabledWrites.writes(x)
      case x: ErrorResponse => newRelicErrorResponseWrites.writes(x)
      case x: IsSupportedJavaVersionResult => newRelicIsSupportedJavaVersionResultWrites.writes(x)
      case x @ Deprovisioned => newRelicDeprovisionedWrites.writes(x)
      case x: GeneratedFiles => newRelicGeneratedFilesWrites.writes(x)
    }

  def unapply(in: JsValue): Option[Request] = Json.fromJson[Request](in).asOpt
}
