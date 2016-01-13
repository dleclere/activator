package activator.typesafeproxy

import java.util.concurrent.TimeoutException

import akka.actor._
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws._
import activator.HttpHelper

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

object SubscriptionDataActor {
  type DoGetSubscriptionData = (AuthenticationStates.AuthenticationData, ActorRef) => Unit

  sealed trait Notification

  def httpGetSubscriptionData(subscriptionDataUrl: String, timeout: FiniteDuration, executionContext: ExecutionContext)(authentication: AuthenticationStates.AuthenticationData, sendTo: ActorRef): Unit = {
    implicit val ec = executionContext
    import SubscriberData._

    def respondWith(result: Try[SubscriberData]): Unit = sendTo ! result

    val req = HttpHelper.proxyHolder(WS.url(subscriptionDataUrl)
      .withHeaders("Accept" -> "application/json",
        "Cookie" -> authentication.toString)
      .withRequestTimeout(timeout.toMillis.intValue))
    req.get() onComplete {
      case Success(response) => respondWith {
        response.status match {
          case 200 =>
            Try(Json.fromJson[SubscriberData](Json.parse(response.body)).get)
          case 401 => Failure(new ProxyInvalidCredentials("Invalid login credentials"))
          case status => Failure(new ProxyFailure(s"Unknown response code: $status"))
        }
      }
      case Failure(exception) => respondWith {
        exception match {
          case x: TimeoutException => Failure(new ProxyTimeout(s"Fetching subscriber data exceeded timeout ${timeout}", x))
          case e => Failure(new ProxyFailure(s"Failed to fetch subscriber data: ${e.getMessage}", e))
        }
      }
    }

  }

  def props(doGetSubscriptionData: SubscriptionDataActor.DoGetSubscriptionData,
    uiActorProps: ActorRef => Props,
    version: Long,
    replyTo: ActorRef,
    websocketActor: ActorRef): Props =
    Props(new SubscriptionDataActor(doGetSubscriptionData, uiActorProps, version, replyTo, websocketActor))

}

class SubscriptionDataActor(doGetSubscriptionData: SubscriptionDataActor.DoGetSubscriptionData,
  uiActorProps: ActorRef => Props,
  version: Long,
  replyTo: ActorRef,
  websocketActor: ActorRef) extends Actor with ActorLogging {
  import TypesafeComProxy._

  private final val uiActor: ActorRef = context.actorOf(uiActorProps(websocketActor))

  def cancel(message: String = "Fetching subscription data canceled by user"): Unit = {
    replyTo ! SubscriberDetail.Put(Failure(new ProxyCanceled(message)), version, self)
    context.become(doStop())
  }

  def onFailure(onRetry: () => Unit): Receive = {
    case UIActor.Cancel => cancel()
    case UIActor.Retry => onRetry()
  }

  def doStop(): Receive = {
    context.watch(uiActor)
    uiActor ! PoisonPill

    {
      case Terminated(`uiActor`) =>
        context stop self
    }
  }

  def handleResult(auth: AuthenticationStates.Authenticated, result: Try[SubscriberData], endReport: UIActor.ReportEndAction, authVersion: Long): Unit = result match {
    case x @ Success(_: SubscriberData.Detail) =>
      replyTo ! SubscriberDetail.Put(x, version, self)
      context.become(doStop())
    case x @ Success(_) =>
      replyTo ! Authentication.Put(Failure(new ProxyInvalidCredentials("Resetting authentication")), authVersion, self)
      replyTo ! SubscriberDetail.Put(x, version, self, false)
      context.become(doStop())
    case Failure(e: ProxyInvalidCredentials) =>
      replyTo ! Authentication.Put(Failure(e), authVersion, self)
      uiActor ! UIActor.RetryableRequests.Failure(e.getMessage, self, retryable = true)
      context.become(onFailure(() => context.become(getAuthentication())))
    case Failure(e: ProxyTimeout) =>
      log.error("Unable to fetch subscriber data", e)
      uiActor ! UIActor.RetryableRequests.Failure(e.getMessage, self, retryable = true)
      context.become(onFailure(() => context.become(runRequest(auth, authVersion))))
    case x @ Failure(e) =>
      log.error("Unknown exception during fetching subscriber data: ", e)
      uiActor ! UIActor.RetryableRequests.Failure(e.getMessage, self, retryable = false)
      replyTo ! SubscriberDetail.Put(x, version, self)
      context.become(doStop())
  }

  def runRequest(auth: AuthenticationStates.Authenticated, authVersion: Long): Receive = {
    val actionId: String = UIActor.genActionId()
    doGetSubscriptionData(auth.authenticationData, self)
    val startReport = UIActor.CancelableRequests.ReportStartAction("Fetching subscription data", actionId, self)
    uiActor ! startReport
    val endReport = startReport.endReport()

    {
      case UIActor.Cancel =>
        uiActor ! endReport
        cancel()
      case x @ Success(_: SubscriberData) =>
        uiActor ! endReport
        handleResult(auth, x.asInstanceOf[Success[SubscriberData]], endReport, authVersion)
      case x: Failure[_] =>
        uiActor ! endReport
        handleResult(auth, x.asInstanceOf[Failure[SubscriberData]], endReport, authVersion)
    }
  }

  def awaitAuthentication(): Receive = {
    case Authentication.Value(Success(x: AuthenticationStates.Authenticated), authVersion) =>
      context.become(runRequest(x, authVersion))
    case Authentication.Value(Failure(e), authVersion) =>
      replyTo ! SubscriberDetail.Put(Failure(e), version, self)
      context.become(doStop())
  }

  def getAuthentication(): Receive = {
    replyTo ! Authentication.Get(self, websocketActor)
    awaitAuthentication()
  }

  def receive: Receive = getAuthentication()
}
