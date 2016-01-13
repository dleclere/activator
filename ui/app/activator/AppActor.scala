/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package activator

import akka.actor._
import java.io.File
import akka.util.Timeout
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.duration._
import play.api.libs.json._
import sbt.client._
import sbt.protocol._

sealed trait AppRequest
case object GetWebSocketCreated extends AppRequest
case object CreateWebSocket extends AppRequest
case class NotifyWebSocket(json: JsObject) extends AppRequest
case object InitialTimeoutExpired extends AppRequest
case class UpdateSourceFiles(files: Set[File]) extends AppRequest
case object ReloadSbtBuild extends AppRequest
case class OpenClient(client: SbtClient) extends AppRequest
case object CloseClient extends AppRequest
case object ProjectFilesChanged extends AppRequest

// requests that need an sbt client
sealed trait ClientAppRequest extends AppRequest {
  def serialId: Long
  def command: Option[String] = None
}
case class RequestExecution(serialId: Long, override val command: Option[String]) extends ClientAppRequest
case class CancelExecution(serialId: Long, executionId: Long) extends ClientAppRequest
case class PossibleAutoCompletions(serialId: Long, override val command: Option[String], detailLevel: Option[Int] = None) extends ClientAppRequest
case class RequestSelfDestruct(serialId: Long) extends ClientAppRequest

sealed trait AppReply
case class SbtClientResponse(serialId: Long, result: Any, command: Option[String] = None) extends AppReply
case object WebSocketAlreadyUsed extends AppReply
case class WebSocketCreatedReply(created: Boolean) extends AppReply

class InstrumentationRequestException(message: String) extends Exception(message)

class AppActor(val config: AppConfig,
  val typesafeComActor: ActorRef,
  val lookupTimeout: Timeout,
  val projectPreprocessor: (ActorRef, ActorRef, AppConfig) => Unit) extends Actor with Stash with ActorLogging {

  AppManager.registerKeepAlive(self)

  def location = config.location

  log.debug(s"Creating AppActor for $location")

  var pending = Vector.empty[(ActorRef, ClientAppRequest)]

  // TODO configName/humanReadableName are cut-and-pasted into AppManager, fix
  val connector = SbtConnector(configName = "activator", humanReadableName = "Activator", location)
  val socket = context.actorOf(Props(new AppWebSocketActor(config, typesafeComActor, lookupTimeout)), name = "socket")
  val projectWatcher = context.actorOf(Props(new ProjectWatcher(location, newSourcesSocket = socket, appActor = self)),
    name = "projectWatcher")
  var sbtClientActor: Option[ActorRef] = None
  var clientCount = 0

  var webSocketCreated = false

  context.watch(socket)
  context.watch(projectWatcher)

  // we can stay alive due to socket connection (and then die with the socket)
  // or else we just die after being around a short time
  context.system.scheduler.scheduleOnce(2.minutes, self, InitialTimeoutExpired)

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  @volatile
  var startedConnecting = System.currentTimeMillis()

  log.debug("Opening SbtConnector")
  connector.open({ client =>
    val now = System.currentTimeMillis()
    val delta = now - startedConnecting

    log.debug(s"Opened connection to sbt for ${location} AppActor=${self.path.name} after ${delta}ms (${delta.toDouble / 1000.0}s)")
    produceLog(LogMessage.DEBUG, s"Opened sbt at '${location}'")
    self ! OpenClient(client)
  }, { (reconnecting, message) =>
    startedConnecting = System.currentTimeMillis()
    log.debug(s"Connection to sbt closed (reconnecting=${reconnecting}: ${message})")
    produceLog(LogMessage.INFO, s"Lost or failed sbt connection: ${message}")
    self ! CloseClient
    if (!reconnecting) {
      log.debug(s"SbtConnector gave up and isn't reconnecting; killing AppActor ${self.path.name}")
      self ! PoisonPill
    }
  })

  def produceLog(level: String, message: String): Unit = {
    // self can be null after we are destroyed
    val selfCopy = self
    if (selfCopy != null)
      selfCopy ! NotifyWebSocket(SbtProtocol.synthesizeLogEvent(level, message))
  }

  private final def handleNotify(body: Receive): Receive =
    ({
      case notify: NotifyWebSocket =>
        if (validateEvent(notify.json)) {
          socket.forward(notify)
        } else {
          log.error("Attempt to send invalid event {}", notify.json)
        }
    }: Receive) orElse body

  private final def handleTerminate(body: Receive): Receive =
    ({
      case Terminated(ref) =>
        if (ref == socket) {
          log.debug(s"socket terminated, killing AppActor ${self.path.name}")
          self ! PoisonPill
        } else if (ref == projectWatcher) {
          log.debug(s"projectWatcher terminated, killing AppActor ${self.path.name}")
          self ! PoisonPill
        } else if (Some(ref) == sbtClientActor) {
          log.debug(s"clientActor terminated, dropping it")
          sbtClientActor = None
        } else if (ref == socket) {
          for (p <- pending) p._1 ! Status.Failure(new RuntimeException("app shut down"))
        }
    }: Receive) orElse body

  private final def handleCommon(body: Receive): Receive = handleNotify(handleTerminate(body))

  private final def running: Receive = handleCommon {

    case req: AppRequest => req match {
      case GetWebSocketCreated =>
        sender ! WebSocketCreatedReply(webSocketCreated)
      case CreateWebSocket =>
        log.debug("got CreateWebSocket")
        if (webSocketCreated) {
          log.debug("Attempt to create websocket for app a second time {}", config.id)
          sender ! WebSocketAlreadyUsed
        } else {
          webSocketCreated = true
          socket.tell(GetWebSocket, sender)
        }
      case InitialTimeoutExpired =>
        if (!webSocketCreated) {
          log.debug("Nobody ever connected to {}, killing it", config.id)
          self ! PoisonPill
        }
      case UpdateSourceFiles(files) =>
        projectWatcher ! SetSourceFilesRequest(files)
      case ReloadSbtBuild =>
        sbtClientActor.foreach(_ ! RequestSelfDestruct(AppActor.playInternalSerialId))
        context.become(preprocess)
      case ProjectFilesChanged =>
        self ! NotifyWebSocket(AppActor.projectFilesChanged)
      case OpenClient(client) =>
        log.debug(s"Old client actor was ${sbtClientActor}")
        sbtClientActor.foreach(_ ! PoisonPill) // shouldn't happen - paranoia

        log.debug(s"Opening new client actor for sbt client ${client}")
        clientCount += 1
        self ! NotifyWebSocket(AppActor.clientOpenedJsonEvent)
        sbtClientActor = Some(context.actorOf(SbtClientActor.props(client), name = s"client-$clientCount"))
        sbtClientActor.foreach(context.watch(_))
        flushPending()
      case CloseClient =>
        log.debug(s"Closing client actor ${sbtClientActor}")
        sbtClientActor.foreach(_ ! PoisonPill) // shouldn't be needed - paranoia
        sbtClientActor = None
        self ! NotifyWebSocket(AppActor.clientClosedJsonEvent)
      case r: ClientAppRequest =>
        pending = pending :+ (sender -> r)
        flushPending()
        if (pending.nonEmpty) {
          produceLog(LogMessage.DEBUG, s"request pending until connection to sbt opens: ${r}")
        }
    }
  }

  private final def preprocess: Receive = handleCommon {
    projectPreprocessor(self, socket, config)

    {
      case ProjectPreprocessor.Finished =>
        context.become(running)
        unstashAll()
      case req: AppRequest => stash()
    }
  }

  override def receive = preprocess

  private def flushPending(): Unit = {
    while (sbtClientActor.isDefined && pending.nonEmpty) {
      val req = pending.head
      pending = pending.tail
      sbtClientActor.foreach { actor =>
        produceLog(LogMessage.DEBUG, s"sending request to sbt ${req._2}")
        actor.tell(req._2, req._1)
      }
    }
    if (pending.nonEmpty)
      log.debug(s"Requests waiting for sbt client to be connected: ${pending}")
  }

  private def validateEvent(json: JsObject): Boolean = {
    // be sure all events have "type" so on the client
    // side we don't check for that.
    val hasType = json \ "type" match {
      case JsString(t) => true
      case _ => false
    }
    hasType
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    log.debug(s"preRestart, ${reason.getClass.getName}: ${reason.getMessage}, on $message")
  }

  override def postStop(): Unit = {
    log.debug("postStop, closing sbt connector")
    connector.close()
  }
}

object AppActor {
  val clientOpenedJsonEvent = SbtProtocol.wrapEvent(JsObject(Nil), "ClientOpened")
  val clientClosedJsonEvent = SbtProtocol.wrapEvent(JsObject(Nil), "ClientClosed")
  val playInternalSerialId = -1L
  val projectFilesChanged = SbtProtocol.wrapEvent(JsObject(Nil), "ProjectFilesChanged")

  def props(config: AppConfig,
    typesafeComActor: ActorRef,
    lookupTimeout: Timeout,
    projectPreprocessor: (ActorRef, ActorRef, AppConfig) => Unit): Props =
    Props(new AppActor(config,
      typesafeComActor,
      lookupTimeout,
      projectPreprocessor))

}
