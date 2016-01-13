package activator.typesafeproxy

import java.util.concurrent.TimeoutException

import akka.actor._
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws._
import activator.HttpHelper

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{ Try, Failure, Success }

object ActivatorLatestActor {
  type DoGetActivatorLatest = ActorRef => Unit

  sealed trait Notification

  def httpGetActivatorLatest(activatorLatestUrl: String, timeout: FiniteDuration, executionContext: ExecutionContext)(sendTo: ActorRef): Unit = {
    implicit val ec = executionContext

    def respondWith(result: Try[ActivatorLatestInfo]): Unit = sendTo ! result

    val req = HttpHelper.proxyHolder(WS.url(activatorLatestUrl)
      .withRequestTimeout(timeout.toMillis.intValue))

    req.get() onComplete {
      case Success(response) => respondWith {
        response.status match {
          case 200 =>
            Try(Json.fromJson[ActivatorLatestInfo](Json.parse(response.body)).get)
          case status =>
            Failure(new ProxyFailure(s"Unexpected response code: $status"))
        }
      }
      case Failure(exception) => respondWith {
        exception match {
          case x: TimeoutException => Failure(new ProxyTimeout(s"Fetching Activator info exceeded timeout ${timeout}", x))
          case e => Failure(new ProxyFailure(s"Failed to fetch Activator info: ${e.getMessage}", e))
        }
      }
    }

  }

  def props(doGetActivatorLatest: ActivatorLatestActor.DoGetActivatorLatest,
    uiActorProps: ActorRef => Props,
    version: Long,
    replyTo: ActorRef,
    websocketActor: ActorRef): Props =
    Props(new ActivatorLatestActor(doGetActivatorLatest, uiActorProps, version, replyTo, websocketActor))

}

class ActivatorLatestActor(doGetActivatorLatest: ActivatorLatestActor.DoGetActivatorLatest,
  uiActorProps: ActorRef => Props,
  version: Long,
  replyTo: ActorRef,
  websocketActor: ActorRef) extends Actor with ActorLogging {
  import TypesafeComProxy._

  private final val uiActor: ActorRef = context.actorOf(uiActorProps(websocketActor))

  def cancel(message: String = "Fetching Activator info canceled by user"): Unit = {
    replyTo ! ActivatorInfo.Put(Failure(new ProxyCanceled(message)), version, self)
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

  def handleResult(result: Try[ActivatorLatestInfo]): Unit = result match {
    case x @ Success(_) =>
      replyTo ! ActivatorInfo.Put(x, version, self)
      context.become(doStop())
    case Failure(e: ProxyTimeout) =>
      log.error("Unable to fetch latest Activator version information", e)
      uiActor ! UIActor.RetryableRequests.Failure(e.getMessage, self, retryable = true)
      context.become(onFailure(() => context.become(runRequest())))
    case x @ Failure(e) =>
      log.error("Unknown exception fetching Activator info", e)
      uiActor ! UIActor.RetryableRequests.Failure(e.getMessage, self, retryable = false)
      replyTo ! ActivatorInfo.Put(x, version, self)
      context.become(doStop())
  }

  def awaitResults(endReport: UIActor.ReportEndAction): Receive = {
    case UIActor.Cancel =>
      uiActor ! endReport
      cancel()
    case x @ Success(_: ActivatorLatestInfo) =>
      uiActor ! endReport
      handleResult(x.asInstanceOf[Success[ActivatorLatestInfo]])
    case x: Failure[_] =>
      uiActor ! endReport
      handleResult(x.asInstanceOf[Failure[ActivatorLatestInfo]])
  }

  def runRequest(): Receive = {
    val actionId: String = UIActor.genActionId()
    doGetActivatorLatest(self)
    val startReport = UIActor.CancelableRequests.ReportStartAction("Fetching Activator Latest Info", actionId, self)
    uiActor ! startReport
    awaitResults(startReport.endReport())
  }

  def receive: Receive = {
    runRequest()
  }
}
