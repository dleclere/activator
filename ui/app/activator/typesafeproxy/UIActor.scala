package activator.typesafeproxy

import java.util.UUID

import akka.actor._
import play.api.libs.functional.syntax._
import play.api.libs.json.Json._
import play.api.libs.json._

// This is an abstract protocol used to communicate to the front-end
object UIActor {
  def genActionId(): String = UUID.randomUUID().toString

  sealed trait Response
  sealed trait CancelableResponse extends Response
  sealed trait RetryableResponse extends Response

  sealed trait CredentialsResponse extends CancelableResponse
  sealed trait FailureResponse extends RetryableResponse
  case object Cancel extends CredentialsResponse with FailureResponse
  case object Retry extends FailureResponse
  case class Credentials(username: String, password: String) extends CredentialsResponse

  sealed trait LocalRequest[+Resp] extends Request[Resp]
  sealed trait Action {
    def actionId: String
  }

  sealed trait CancelableRequest[T <: CancelableResponse] extends LocalRequest[T] {
    def cancel()(implicit sender: ActorRef): Unit = response(Cancel)
  }
  sealed trait RetryableRequest[T <: RetryableResponse] extends LocalRequest[T] {
    def cancel()(implicit sender: ActorRef): Unit = response(Cancel)
    def retry()(implicit sender: ActorRef): Unit = response(Retry)
  }

  object CancelableRequests {
    case class RequestCredentials(sendTo: ActorRef, message: Option[String] = None) extends CancelableRequest[CredentialsResponse] {
      def credentials(username: String, password: String)(implicit sender: ActorRef): Unit =
        response(Credentials(username, password))
    }

    case class ReportStartAction(message: String, actionId: String, sendTo: ActorRef) extends CancelableRequest[Nothing] with Action {
      def endReport(endSendTo: ActorRef = sendTo): ReportEndAction = ReportEndAction(message, actionId, endSendTo)
    }
  }

  case class ReportEndAction(message: String, actionId: String, sendTo: ActorRef) extends LocalRequest[Nothing] with Action

  object RetryableRequests {
    case class Failure(message: String, sendTo: ActorRef, retryable: Boolean) extends RetryableRequest[Nothing]
  }

  object WebSocket {
    import activator.JsonHelper._
    val requestTag = "TypesafeComProxy"
    val responseTag = requestTag

    sealed trait Response {
      def actorPath: String
    }

    sealed trait LocalRequest[+T <: Response] {
      def actorPath: String
    }

    sealed trait Action {
      def actionId: String
    }

    sealed trait CancelableResponse extends Response
    sealed trait RetryableResponse extends Response

    sealed trait CredentialsResponse extends CancelableResponse
    sealed trait FailureResponse extends RetryableResponse

    case class Cancel(actorPath: String) extends CredentialsResponse with FailureResponse
    case class Retry(actorPath: String) extends FailureResponse
    case class Failure(message: String, retryable: Boolean, actorPath: String) extends LocalRequest[FailureResponse]

    case class Credentials(username: String, password: String, actorPath: String) extends CredentialsResponse
    case class RequestCredentials(message: Option[String], actorPath: String) extends LocalRequest[CredentialsResponse]

    case class ReportStartAction(message: String, actionId: String, actorPath: String) extends LocalRequest[CancelableResponse] with Action
    case class ReportEndAction(message: String, actionId: String, actorPath: String) extends LocalRequest[Nothing] with Action

    implicit val websocketReads: Reads[Response] =
      extractMessage[Response](requestTag)(new Reads[Response] {
        def reads(in: JsValue): JsResult[Response] = ((in \ "type"), (in \ "actorPath")) match {
          case (JsString("cancel"), JsString(ap)) =>
            JsSuccess(Cancel(ap))
          case (JsString("retry"), JsString(ap)) => JsSuccess(Retry(ap))
          case (JsString("credentials"), JsString(ap)) =>
            ((__ \ "username").read[String] and (__ \ "password").read[String])((un, pw) => Credentials.apply(un, pw, ap)).reads(in)
          case x => JsError(s"expected one of 'cancel', 'retry' or 'credentials', got $x")
        }
      })

    implicit val websocketWrites: Writes[LocalRequest[_ <: Response]] =
      emitMessage(responseTag)(_ match {
        case RequestCredentials(None, ap) => Json.obj("type" -> "requestCredentials", "actorPath" -> ap)
        case RequestCredentials(Some(m), ap) => Json.obj("type" -> "requestCredentials", "message" -> m, "actorPath" -> ap)
        case ReportStartAction(m, aid, ap) => Json.obj("type" -> "reportStartAction", "message" -> m, "actionId" -> aid, "actorPath" -> ap)
        case ReportEndAction(m, aid, ap) => Json.obj("type" -> "reportEndAction", "message" -> m, "actionId" -> aid, "actorPath" -> ap)
        case Failure(m, r, ap) => Json.obj("type" -> "failure", "message" -> m, "retryable" -> r, "actorPath" -> ap)
      })

    object Inbound {
      def unapply(in: JsValue): Option[Response] = Json.fromJson[Response](in).asOpt
    }

    object Outbound {
      def unapply(in: Any): Option[LocalRequest[_ <: Response]] = in match {
        case x: LocalRequest[_] => Some(x)
        case _ => None
      }
    }
  }

  def props(websocketsActor: ActorRef): Props = Props(new UIActor(websocketsActor))

}

class UIActor(websocketsActor: ActorRef) extends Actor with ActorLogging {
  import UIActor._

  def proxyInteraction(awaiting: Option[LocalRequest[_]]): Receive = {
    case x: WebSocket.Response =>
      (x, awaiting) match {
        case (_: WebSocket.Cancel, Some(a: CancelableRequest[_])) => a.cancel()
        case (_: WebSocket.Cancel, Some(a: RetryableRequest[_])) => a.cancel()
        case (msg: WebSocket.Credentials, Some(a: CancelableRequests.RequestCredentials)) => a.credentials(msg.username, msg.password)
        case (_: WebSocket.Retry, Some(a: RetryableRequest[_])) => a.retry()
        case (_, _) => // Ignore
      }
      context.become(proxyInteraction(None))
    case x: CancelableRequests.ReportStartAction =>
      websocketsActor ! WebSocket.ReportStartAction(x.message, x.actionId, self.path.toString)
      context.become(proxyInteraction(Some(x)))
    case x: ReportEndAction =>
      websocketsActor ! WebSocket.ReportEndAction(x.message, x.actionId, self.path.toString)
      context.become(proxyInteraction(None))
    case x: CancelableRequests.RequestCredentials =>
      websocketsActor ! WebSocket.RequestCredentials(x.message, self.path.toString)
      context.become(proxyInteraction(Some(x)))
    case x: RetryableRequests.Failure =>
      websocketsActor ! WebSocket.Failure(x.message, x.retryable, self.path.toString)
      context.become(proxyInteraction(Some(x)))
  }

  def receive: Receive = proxyInteraction(None)
}
