package activator.typesafeproxy

import java.util.concurrent.TimeoutException

import akka.actor._
import play.api.Play.current
import play.api.libs.ws._
import activator.HttpHelper

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

sealed trait AuthenticationState
object AuthenticationStates {
  type AuthenticationData = WSCookie

  val emptyAuthentication: AuthenticationData = new WSCookie {
    val domain: String = "domain"
    val expires: Option[Long] = None
    val maxAge: Option[Int] = None
    val name: Option[String] = None
    val path: String = "path"
    val secure: Boolean = true
    def underlying[T]: T = ???
    val value: Option[String] = Some("value")
  }

  case class Authenticated(authenticationData: AuthenticationData) extends AuthenticationState
}

object AuthenticationActor {
  type DoAuthenticate = (String, String, ActorRef) => Unit

  def httpDoAuthenticate(authenticationUrl: String, authenticateTimeout: FiniteDuration, executionContext: ExecutionContext)(username: String, password: String, sendTo: ActorRef): Unit = {
    implicit val ec = executionContext

    def respondWith(result: Try[AuthenticationState]): Unit = sendTo ! result

    val req = HttpHelper.proxyHolder(WS.url(authenticationUrl)
      .withHeaders("Accept" -> "application/json")
      .withRequestTimeout(authenticateTimeout.toMillis.intValue))
    req.post(Map("email" -> Seq(username), "password" -> Seq(password))) onComplete {
      case Success(response) => respondWith {
        response.status match {
          case 200 => Success(AuthenticationStates.Authenticated(response.cookie("PLAY_SESSION").get))
          case 401 => Failure(new ProxyInvalidCredentials("Invalid login credentials"))
          case 400 => Failure(new ProxyInvalidCredentials("Missing email or password"))
          case status => Failure(new ProxyFailure(s"Unknown response code: $status"))
        }
      }
      case Failure(exception) => respondWith {
        exception match {
          case x: TimeoutException => Failure(new ProxyTimeout(s"Authentication exceeded timeout ${authenticateTimeout}", x))
          case e => Failure(new ProxyFailure(s"Authentication failure: ${e.getMessage}", e))
        }
      }
    }

  }

  def props(doAuthenticate: AuthenticationActor.DoAuthenticate,
    uiActorProps: ActorRef => Props,
    version: Long,
    replyTo: ActorRef,
    websocketActor: ActorRef,
    initMessage: Option[String] = None): Props =
    Props(new AuthenticationActor(doAuthenticate, uiActorProps, version, replyTo, websocketActor, initMessage))

}

class AuthenticationActor(doAuthenticate: AuthenticationActor.DoAuthenticate,
  uiActorProps: ActorRef => Props,
  version: Long,
  replyTo: ActorRef,
  websocketActor: ActorRef,
  initMessage: Option[String]) extends Actor with ActorLogging {
  import TypesafeComProxy._

  private final val uiActor: ActorRef = context.actorOf(uiActorProps(websocketActor))

  def cancel(message: String = "Authentication canceled by user"): Unit = {
    replyTo ! Authentication.Put(Failure(new ProxyCanceled(message)), version, self)
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

  def handleResult(username: String, password: String, result: Try[AuthenticationState]): Unit = result match {
    case x @ Success(_) =>
      replyTo ! Authentication.Put(x, version, self)
      context.become(doStop())
    case Failure(e: ProxyInvalidCredentials) =>
      uiActor ! UIActor.RetryableRequests.Failure(e.getMessage, self, retryable = true)
      context.become(onFailure(() => context.become(run())))
    case Failure(e: ProxyTimeout) =>
      log.error("Unable to authenticate", e)
      uiActor ! UIActor.RetryableRequests.Failure(e.getMessage, self, retryable = true)
      context.become(onFailure(() => context.become(authenticating(username, password))))
    case x @ Failure(e) =>
      log.error("Unknown exception during authentication", e)
      uiActor ! UIActor.RetryableRequests.Failure(e.getMessage, self, retryable = false)
      replyTo ! Authentication.Put(x, version, self)
      context.become(doStop())
  }

  def authenticating(username: String, password: String): Receive = {
    val actionId: String = UIActor.genActionId()
    val startReport = UIActor.CancelableRequests.ReportStartAction("Checking credentials against typesafe.com", actionId, self)
    uiActor ! startReport
    doAuthenticate(username, password, self)
    val endReport = startReport.endReport()

    {
      case UIActor.Cancel =>
        uiActor ! endReport
        cancel()
      case x @ Success(_: AuthenticationState) =>
        uiActor ! endReport
        handleResult(username, password, x.asInstanceOf[Success[AuthenticationState]])
      case x: Failure[_] =>
        uiActor ! endReport
        handleResult(username, password, x.asInstanceOf[Failure[AuthenticationState]])
    }
  }

  def awaitCredentials(): Receive = {
    def onCredentialsResponse(msg: UIActor.CredentialsResponse): Unit = msg match {
      case UIActor.Credentials(username, password) =>
        context.become(authenticating(username, password))
      case UIActor.Cancel => cancel()
    }

    {
      case x: UIActor.CredentialsResponse => onCredentialsResponse(x)
    }

  }

  def run(message: Option[String] = None): Receive = {
    uiActor ! UIActor.CancelableRequests.RequestCredentials(self, message)
    awaitCredentials()
  }

  def receive: Receive = run(initMessage)

}
