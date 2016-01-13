package activator

import akka.actor._
import akka.pattern._
import play.api.libs.json._

import sbt.client._
import sbt.protocol._

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.concurrent.ExecutionContext.Implicits.global

class SbtClientActor(val client: SbtClient) extends Actor with ActorLogging {
  log.debug(s"Creating SbtClientActor ${self.path.name}")

  import SbtClientActor._

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  // Initialize the life cycle handler for the sbt client actor
  val lifeCycleHandler = context.actorOf(SbtClientLifeCycleHandlerActor.props(client), "lifeCycleHandler-" + self.path.name)
  lifeCycleHandler ! SbtClientLifeCycleHandlerActor.Initialize

  def forwardOverSocket(event: Event): Unit = {
    context.parent ! NotifyWebSocket(SbtProtocol.wrapEvent(event))
  }

  def produceLog(level: String, message: String): Unit = {
    context.parent ! NotifyWebSocket(SbtProtocol.synthesizeLogEvent(level, message))
  }

  override def receive = {
    case event: Event => event match {
      case _: ClosedEvent =>
        self ! PoisonPill
      case _: BuildStructureChanged =>
        // this should not happen unless during development, hence the error level
        log.error(s"Received event which should have been filtered out by SbtClient ${event}")
      case changed: ValueChanged => forwardOverSocket(changed)
      case entry: LogEvent => entry match {
        case e: DetachedLogEvent => forwardOverSocket(e)
        case e: TaskLogEvent => forwardOverSocket(e)
        case e: BackgroundJobLogEvent => forwardOverSocket(e)
      }
      case fail: ExecutionFailure => forwardOverSocket(fail)
      case yay: ExecutionSuccess => forwardOverSocket(yay)
      case starting: ExecutionStarting => forwardOverSocket(starting)
      case waiting: ExecutionWaiting => forwardOverSocket(waiting)
      case finished: TaskFinished => forwardOverSocket(finished)
      case started: TaskStarted => forwardOverSocket(started)
      case taskEvent: TaskEvent => forwardOverSocket(taskEvent)
      case detachedEvent: DetachedEvent => forwardOverSocket(detachedEvent)
      case loaded: BuildLoaded => forwardOverSocket(loaded)
      case failed: BuildFailedToLoad => forwardOverSocket(failed)
      case background: BackgroundJobEvent => forwardOverSocket(background)
      case background: BackgroundJobStarted => forwardOverSocket(background)
      case background: BackgroundJobFinished => forwardOverSocket(background)
    }
    case structure: MinimalBuildStructure =>
      forwardOverSocket(BuildStructureChanged(structure))
    case req: ClientAppRequest => {
      req match {
        case re: RequestExecution =>
          log.debug("requesting execution of " + re.command)
          client.requestExecution(re.command.get, interaction = None)
        case ce: CancelExecution =>
          log.debug("canceling execution " + ce.executionId)
          client.cancelExecution(ce.executionId)
        case pac: PossibleAutoCompletions =>
          log.debug("possible autocompletions for " + pac.command.get)
          client.possibleAutocompletions(pac.command.get, detailLevel = pac.detailLevel.getOrElse(0))
        case rsd: RequestSelfDestruct =>
          log.debug("Asking sbt to exit")
          client.requestSelfDestruct()
          Future.successful(None)
      }
    } recover {
      case NonFatal(e) =>
        log.debug(s"request to sbt failed ${e.getMessage}")
        produceLog(LogMessage.DEBUG, s"request $req failed: ${e.getClass.getName}: ${e.getMessage}")
        Status.Failure(e)
    } map { result =>
      log.debug(s"${req} result: ${result}")
      produceLog(LogMessage.DEBUG, s"request $req result: ${result}")
      SbtClientResponse(req.serialId, result, req.command)
    } pipeTo sender
  }
}

object SbtClientActor {
  def props(client: SbtClient) = Props(new SbtClientActor(client))
  case class PlayAvailable(available: Boolean)
}

/**
 * Sets up all subscriptions to the sbt client required.
 * Forwards all messages to its parent.
 * Takes care of resources during life cycle changes.
 */
class SbtClientLifeCycleHandlerActor(val client: SbtClient) extends Actor with ActorLogging {
  import SbtClientLifeCycleHandlerActor._
  var eventsSub: Option[Subscription] = None
  var buildSub: Option[Subscription] = None
  var valueSub: Option[Subscription] = None

  def receive = {
    case Initialize =>
      handleEvents
      watchBuild
      setupSubscription
  }

  override def postStop(): Unit = {
    log.debug("postStop")
    eventsSub map { _.cancel() }
    buildSub map { _.cancel() }
    valueSub map { _.cancel() }
    // we were probably stopped because the client closed already,
    // but if not, close here.
    client.close()
  }

  def handleEvents = {
    eventsSub = Some(client.handleEvents { event =>
      context.parent ! event
    })
  }

  def watchBuild = {
    buildSub = Some(client.watchBuild { structure =>
      context.parent ! structure
    })
  }

  // this is a hardcoded hack... we need to control the list of things
  // to watch from JS, and we should handle build structure changes
  // by redoing this
  def setupSubscription = {
    valueSub = Some(new Subscription() {

      private def forward(key: ScopedKey, result: TaskResult): Unit =
        Option(context).foreach(_.parent ! ValueChanged(key, result))

      val eagerSubs: Seq[Subscription] =
        Seq("discoveredMainClasses",
          "mainClass") map { name =>
            client.rawWatch(name)(forward)
          }

      val lazySubs: Seq[Subscription] =
        Seq[String]() map { name =>
          client.rawLazyWatch(name)(forward)
        }

      override def cancel(): Unit = {
        (eagerSubs ++ lazySubs) map { sub => sub.cancel() }
      }
    })
  }
}

object SbtClientLifeCycleHandlerActor {
  def props(client: SbtClient) = Props(new SbtClientLifeCycleHandlerActor(client))
  case object Initialize
}
