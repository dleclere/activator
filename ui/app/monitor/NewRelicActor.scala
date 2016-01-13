/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */

package monitor

import java.io.File

import akka.actor.{ ActorRef, _ }
import akka.event.LoggingAdapter
import activator.NewRelic

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

object NewRelicActor {
  def props(config: NewRelic.Config,
    executionContext: ExecutionContext): Props =
    Props(new NewRelicActor(new Underlying(config)(_)(executionContext)))

  def unapply(in: Any): Option[InternalRequest] = in match {
    case r: InternalRequest => Some(r)
    case _ => None
  }

  sealed trait InternalRequest {
    def error(message: String): InternalResponse =
      InternalErrorResponse(message, this)
  }

  case class InternalProvision(notificationSink: ActorRef) extends InternalRequest {
    def response: InternalResponse = InternalProvisioned(this)
  }

  case object InternalDeprovision extends InternalRequest {
    def response: InternalResponse = Deprovisioned
  }

  case object InternalAvailable extends InternalRequest {
    def response(result: Boolean): InternalResponse = InternalAvailableResponse(result, this)
  }

  case class InternalEnableProject(destination: File, key: String, appName: String) extends InternalRequest {
    def response: InternalResponse = InternalProjectEnabled(this)
  }

  case class InternalIsProjectEnabled(destination: File) extends InternalRequest {
    def response(result: Boolean): InternalResponse = InternalIsProjectEnabledResult(result, this)
  }

  case object InternalIsSupportedJavaVersion extends InternalRequest {
    def response(result: Boolean, version: String): InternalResponse = InternalIsSupportedJavaVersionResult(result, version, this)
  }

  case class InternalGenerateFiles(location: String, appConfig: File) extends InternalRequest {
    def response = InternalGenerateFilesResult(this)
  }

  sealed trait InternalResponse {
    def request: InternalRequest
  }
  case class InternalProvisioned(request: InternalProvision) extends InternalResponse
  case object Deprovisioned extends InternalResponse {
    val request: InternalRequest = InternalDeprovision
  }
  case class InternalErrorResponse(message: String, request: InternalRequest) extends InternalResponse
  case class InternalAvailableResponse(result: Boolean, request: InternalRequest) extends InternalResponse
  case class InternalProjectEnabled(request: InternalRequest) extends InternalResponse
  case class InternalIsProjectEnabledResult(result: Boolean, request: InternalRequest) extends InternalResponse
  case class InternalIsSupportedJavaVersionResult(result: Boolean, version: String, request: InternalRequest) extends InternalResponse
  case class InternalGenerateFilesResult(request: InternalRequest) extends InternalResponse

  class Underlying(config: NewRelic.Config)(log: LoggingAdapter)(implicit ec: ExecutionContext) {
    import monitor.Provisioning._

    def reportError(error: Throwable, message: String, request: InternalRequest, sender: ActorRef): Unit = {
      log.error(error, message)
      sender ! request.error(message)
    }

    def onMessage(request: InternalRequest, sender: ActorRef, self: ActorRef, context: ActorContext): Unit = request match {
      case r @ InternalProvision(sink) =>
        val ns = actorWrapper(sink)
        provision(
          simpleDownloadExecutor(defaultWSClient,
            config.url, ns, config.timeout),
          config.verifyFile,
          config.extractRoot(), ns) onComplete {
            case Success(_) => sender ! r.response
            case Failure(error) =>
              reportError(error, s"Error processing provisioning request: ${error.getMessage}", r, sender)
          }
      case r @ InternalDeprovision => try {
        NewRelic.deprovision(config.extractRoot())
        sender ! r.response
      } catch {
        case e: Exception =>
          log.error(e, "Failure deprovisioning New Relic")
          sender ! r.error(s"Failure deprovisioning New Relic: ${e.getMessage}")
      }
      case r @ InternalAvailable => try {
        sender ! r.response(NewRelic.hasNewRelic(config.extractRoot()))
      } catch {
        case e: Exception =>
          log.error(e, "Failure during New Relic availability check")
          sender ! r.error(s"Failure during New Relic availability check: ${e.getMessage}")
      }
      case r @ InternalEnableProject(destination, key, name) => try {
        NewRelic.provisionNewRelic(config.extractRoot(), destination, key, name)
        sender ! r.response
      } catch {
        case e: Exception =>
          log.error(e, "Failure during enabling project")
          sender ! r.error(s"Failure during enabling project: ${e.getMessage}")
      }
      case r @ InternalIsProjectEnabled(destination) => try {
        sender ! r.response(NewRelic.isProjectEnabled(destination))
      } catch {
        case e: Exception =>
          log.error(e, "Failure testing if project enabled for New Relic")
          sender ! r.error(s"Failure testing if project enabled for New Relic: ${e.getMessage}")
      }
      case r @ InternalIsSupportedJavaVersion => try {
        val version = System.getProperty("java.version")
        sender ! r.response(config.supportJavaVersionsPattern.matcher(version).matches(), version)
      } catch {
        case e: Exception =>
          log.error(e, "Failure checking for supported Java version for New Relic")
          sender ! r.error(s"Failure checking for supported Java version for New Relic: ${e.getMessage}")
      }
      case r @ InternalGenerateFiles(location, root) => try {
        NewRelic.generateFiles(location, config, context.system.settings.config, root)
        sender ! r.response
      } catch {
        case e: Exception =>
          log.error(e, "Failure creating sbt files required for New Relic support")
          sender ! r.error(s"Failure creating sbt files required for New Relic support: ${e.getMessage}")
      }
    }
  }
}

class NewRelicActor(newRelicBuilder: LoggingAdapter => NewRelicActor.Underlying) extends Actor with ActorLogging {
  val newRelic = newRelicBuilder(log)

  def receive: Receive = {
    case r: NewRelicActor.InternalRequest => newRelic.onMessage(r, sender, self, context)
  }
}
