/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package activator

import java.util.UUID
import akka.util.Timeout

import scala.concurrent.Future
import java.io.File
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Promise
import akka.pattern._
import play.Logger
import akka.actor._
import scala.concurrent.Await
import scala.concurrent.duration._
import play.api.libs.json.JsObject
import java.util.concurrent.atomic.AtomicInteger
import scala.util.{ Failure, Success }
import scala.util.control.NonFatal
import activator._

sealed trait AppCacheRequest

case class GetOrCreateApp(id: AppIdSocketId) extends AppCacheRequest
case class GetApp(socketId: UUID) extends AppCacheRequest
case class ForgetApp(appId: String) extends AppCacheRequest
case object Cleanup extends AppCacheRequest

sealed trait AppCacheReply

case class GotApp(app: activator.App) extends AppCacheReply
case object ForgotApp extends AppCacheReply

private case class CachedApp(appId: String, futureApp: Future[activator.App])

class AppCacheActor(val typesafeComActor: ActorRef,
  val lookupTimeout: Timeout,
  val projectPreprocessor: (ActorRef, ActorRef, AppConfig) => Unit) extends Actor with ActorLogging {
  private var appCache: Map[UUID, CachedApp] = Map.empty

  override val supervisorStrategy = SupervisorStrategy.stoppingStrategy

  private def cleanup(deadRef: Option[ActorRef]): Unit = {
    appCache = appCache.filter {
      case (socketId, cached) =>
        if (cached.futureApp.isCompleted) {
          try {
            // this should be "instant" but 5 seconds to be safe
            val app = Await.result(cached.futureApp, 5.seconds)
            if (Some(app.actor) == deadRef || app.isTerminated) {
              log.debug("cleaning up terminated app actor {} {}", socketId, app.actor)
              false
            } else {
              //log.debug("keeping live app actor {} {}", id, app.actor)
              true
            }
          } catch {
            case e: Exception =>
              log.debug("cleaning up app {} which failed to load due to '{}'", socketId, e.getMessage)
              false
          }
        } else {
          // still pending, keep it
          log.debug("app actor {} still pending start", socketId)
          true
        }
    }
  }

  override def receive = {
    case Terminated(ref) =>
      cleanup(Some(ref))

    case req: AppCacheRequest => req match {
      case GetOrCreateApp(id) =>
        appCache.get(id.socketId) match {
          case Some(cached) =>
            log.debug(s"returning existing app from app cache for $id")
            cached.futureApp map { a =>
              log.debug(s"existing app ${a.id} terminated=${a.isTerminated}")
              GotApp(a)
            } pipeTo sender
          case None => {
            val appFuture: Future[activator.App] = AppManager.loadConfigFromAppId(id.appId) map { config =>
              log.debug(s"creating a new app for $id")
              val appActorBuilder: AppConfig => Props = AppActor.props(_, typesafeComActor, lookupTimeout, projectPreprocessor)
              new activator.App(id, config, activator.Akka.system, appActorBuilder)
            }

            appCache += (id.socketId -> CachedApp(id.appId, appFuture))

            // set up to watch the app's actor, or forget the future
            // if the app is never created
            appFuture.onComplete { value =>
              log.debug(s"Completed app future for ${id} with ${value}")
              value.foreach { app =>
                context.watch(app.actor)
              }
              if (value.isFailure)
                self ! Cleanup
            }

            appFuture.map(GotApp(_)).pipeTo(sender)
          }
        }
      case GetApp(socketId) =>
        appCache.get(socketId) match {
          case Some(cached) =>
            log.debug(s"returning existing app from app cache for $socketId")
            cached.futureApp map { a =>
              log.debug(s"existing app ${a.id} terminated=${a.isTerminated}")
              GotApp(a)
            } pipeTo sender
          case None => {
            sender ! Status.Failure(new RuntimeException(s"No app found with socket ID $socketId, we have these ids: ${appCache.keys}"))
          }
        }
      case ForgetApp(appId) =>
        appCache.find(_._2.appId == appId) match {
          case Some(_) =>
            log.debug(s"Attempt to forget in-use app $appId")
            sender ! Status.Failure(new Exception("This app is currently in use"))
          case None =>
            RootConfig.rewriteUser { root =>
              root.copy(applications = root.applications.filterNot(_.id == appId))
            } map { _ =>
              ForgotApp
            } pipeTo sender
        }
      case Cleanup =>
        cleanup(None)
    }
  }

  override def postStop() = {
    log.debug("postStop")
  }

  override def preRestart(reason: Throwable, message: Option[Any]) = {
    log.debug("preRestart {} {}", reason, message)
  }
}

sealed trait KeepAliveRequest
case class RegisterKeepAlive(ref: ActorRef) extends KeepAliveRequest
case class CheckForExit(serial: Int) extends KeepAliveRequest

// Note: we only CheckForExit on the transition from
// 1 to 0 keep alives, so we do not exit just because
// no keep alive has ever been added. This means there is
// infinite time on startup to wait for a browser tab to
// be opened.
class KeepAliveActor extends Actor with ActorLogging {
  var keepAlives = Set.empty[ActorRef]
  // this increments on keepAlives mutation, allowing us to decide
  // whether a CheckForExit is still valid or should be dropped
  var serial = 0

  def receive = {
    case Terminated(ref) =>
      log.debug("terminated {}", ref)
      if (keepAlives.contains(ref)) {
        log.debug("Removing ref from keep alives {}", ref)
        keepAlives -= ref
        serial += 1
      } else {
        log.debug("Ref was not in the keep alives set {}", ref)
      }
      if (keepAlives.isEmpty) {
        log.debug("scheduling CheckForExit")
        context.system.scheduler.scheduleOnce(60.seconds, self, CheckForExit(serial))
      }
    case req: KeepAliveRequest => req match {
      case RegisterKeepAlive(ref) =>
        log.debug("Actor will keep us alive {}", ref)
        keepAlives += ref
        serial += 1
        context.watch(ref)
      case CheckForExit(validitySerial) =>
        if (validitySerial == serial) {
          log.debug("checking for exit, keepAlives={}", keepAlives)
          if (keepAlives.isEmpty) {
            log.info("Activator doesn't seem to be open in any browser tabs, so shutting down.")
            self ! PoisonPill
          }
        } else {
          log.debug("Something changed since CheckForExit scheduled, disregarding")
        }
    }
  }

  override def postStop() {
    log.debug("postStop")
    log.info("Exiting.")
    // TODO - Not in debug mode
    val debugMode = sys.props.get("activator.runinsbt").map(_ == "true").getOrElse(false)
    if (!debugMode) System.exit(0)
    else log.info("Would have killed activator if we weren't in debug mode.")
  }
}

object AppManager {

  private val keepAlive = activator.Akka.system.actorOf(Props(new KeepAliveActor), name = "keep-alive")

  def registerKeepAlive(ref: ActorRef): Unit = {
    keepAlive ! RegisterKeepAlive(ref)
  }

  val appCache = activator.Akka.system.actorOf(Props(new AppCacheActor(controllers.Application.typesafeComActor, controllers.Application.lookupTimeout, ProjectPreprocessor.defaultPreprocessor _)), name = "app-cache")

  val requestManagerCount = new AtomicInteger(1)

  // Loads an application based on its id.
  // This needs to look in the RootConfig for the App/Location
  // based on this ID.
  // If the app id does not exist ->
  //    Return error
  // If it exists
  //    Return the app
  def getOrCreateApp(id: AppIdSocketId): Future[activator.App] = {
    implicit val timeout = Akka.longTimeoutThatIsAProblem
    (appCache ? GetOrCreateApp(id)).map {
      case GotApp(app) => app
    }
  }

  def getApp(socketId: UUID): Future[activator.App] = {
    implicit val timeout = Akka.longTimeoutThatIsAProblem
    (appCache ? GetApp(socketId)).map {
      case GotApp(app) => app
    }
  }

  // Loads the ID of an app based on the CWD.
  // If we don't have an ID in RootConfig for this location, then
  // - we should load the app and determine a good id
  // - we should store the id/location in the RootConfig
  // - We should return the new ID or None if this location is not an App.
  def loadAppIdFromLocation(location: File, eventHandler: Option[JsObject => Unit] = None): Future[ProcessResult[String]] = {
    val absolute = location.getAbsoluteFile()
    RootConfig.user.applications.find(_.location == absolute) match {
      case Some(app) => Promise.successful(ProcessSuccess(app.id)).future
      case None => {
        doInitialAppAnalysis(location, eventHandler) map { _.map(_.id) }
      }
    }
  }

  def loadConfigFromAppId(id: String): Future[activator.AppConfig] = {
    RootConfig.user.applications.find(_.id == id) match {
      case Some(config) =>
        if (!new java.io.File(config.location, "project/build.properties").exists()) {
          Promise.failed(new RuntimeException(s"${config.location} does not contain a valid sbt project")).future
        } else {

          Promise.successful(config).future
        }
      case whatever =>
        Promise.failed(new RuntimeException("No such app with id: '" + id + "'")).future
    }
  }

  def forgetApp(id: String): Future[Unit] = {
    implicit val timeout = Akka.longTimeoutThatIsAProblem
    (appCache ? ForgetApp(id)).map(_ => ())
  }

  // choose id "name", "name-1", "name-2", etc.
  // should always be called inside rewriteUser to avoid
  // a race creating the same ID
  private def newIdFromName(root: RootConfig, name: String, suffix: Int = 0): String = {
    val candidate = name + (if (suffix > 0) "-" + suffix.toString else "")
    root.applications.find(_.id == candidate) match {
      case Some(app) => newIdFromName(root, name, suffix + 1)
      case None => candidate
    }
  }

  // FIXME we need to send events here or somehow be sure they are displayed
  // by the client side. Need sbt logs in the UI. Also failure-to-start-sbt
  // errors should go there.
  private def doInitialAppAnalysis(location: File, eventHandler: Option[JsObject => Unit] = None): Future[ProcessResult[AppConfig]] = {
    import sbt.client._
    import sbt.protocol._
    import sbt.serialization._

    val validated = ProcessSuccess(location).validate(
      Validation.isDirectory,
      Validation.looksLikeAnSbtProject)

    validated flatMapNested { location =>
      implicit val timeout = Akka.longTimeoutThatIsAProblem;

      // TODO factor out the configName / humanReadableName to share with
      // AppActor
      val connector = SbtConnector(configName = "activator",
        humanReadableName = "Activator", location)

      val nameFuture = {
        val namePromise = Promise[String]()
        val nameFuture = namePromise.future
        def onConnect(client: SbtClient): Unit = {

          val eventsSub = client.handleEvents({ event =>
            import sbt.protocol._

            val json = event match {
              case log: LogEvent => log match {
                case e: TaskLogEvent => SbtProtocol.wrapEvent(e)
                case e: DetachedLogEvent => SbtProtocol.wrapEvent(e)
                case e: BackgroundJobLogEvent => SbtProtocol.wrapEvent(e)
              }
              case e: BuildLoaded => SbtProtocol.wrapEvent(e)
              case e: BuildFailedToLoad => SbtProtocol.wrapEvent(e)
              case e: DetachedEvent => SbtProtocol.wrapEvent(e)
              case _ =>
                SbtProtocol.synthesizeLogEvent(LogMessage.DEBUG, event.toString)
            }
            eventHandler.foreach(_.apply(json))

            event match {
              // if we can load the build, get the name
              case _: BuildLoaded =>
                client.lookupScopedKey("name") map { keys =>
                  if (keys.isEmpty) {
                    namePromise.tryFailure(new RuntimeException("Project has no 'name' setting"))
                  } else {
                    val sub =
                      client.watch[String](SettingKey[String](keys.head)) { (key, result) =>
                        result match {
                          case Success(name) => namePromise.trySuccess(name)
                          case Failure(e) => namePromise.tryFailure(new RuntimeException(s"Failed to get name setting from project: ${e.toString}"))
                        }
                      }
                    nameFuture.onComplete { _ => sub.cancel() }
                  }
                }
              // if we can't load the build, give up on name
              case _: BuildFailedToLoad =>
                namePromise.tryFailure(new RuntimeException("Failed to load the build"))
              case _ =>
            }
          })
          nameFuture.onComplete { _ => eventsSub.cancel() }
        }
        def onError(reconnecting: Boolean, message: String): Unit = {
          if (reconnecting) {
            // error for reason other than close
            Logger.debug(s"Error connecting to sbt: ${message}")
          } else {
            // this happens on our explicit close, but should be a no-op if we've already
            // gotten the project name
            Logger.debug(s"Error connecting to sbt (probably just closed): ${message}")
            namePromise.tryFailure(new RuntimeException("Connection to sbt closed without acquiring name of project"))
          }
        }

        connector.open(onConnect, onError)

        nameFuture.onComplete { _ =>
          Logger.debug("Closing sbt connector used to get project name")
          connector.close()
        }

        nameFuture
      }

      val resultFuture: Future[ProcessResult[AppConfig]] =
        nameFuture map { name =>
          Logger.debug("got project name from sbt: '" + name + "'")
          name
        } recover {
          case NonFatal(e) =>
            // here we need to just recover, because if you can't open the app
            // you can't work on it to fix it
            Logger.debug(s"error getting name from sbt: ${e.getClass.getName}: ${e.getMessage}")
            val name = location.getName
            Logger.debug("using file basename as app name: " + name)
            name
        } flatMap { name =>
          RootConfig.rewriteUser { root =>
            val oldConfig = root.applications.find(_.location == location)
            val now = System.currentTimeMillis
            val createdTime = oldConfig.flatMap(_.createdTime).getOrElse(now)
            val usedTime = now
            val config = AppConfig(id = newIdFromName(root, name), cachedName = Some(name),
              createdTime = Some(createdTime), usedTime = Some(usedTime), location = location)
            val newApps = root.applications.filterNot(_.location == config.location) :+ config
            root.copy(applications = newApps)
          } map { Unit =>
            import ProcessResult.opt2Process
            RootConfig.user.applications.find(_.location == location)
              .validated(s"Somehow failed to save new app at ${location.getPath} in config")
          }
        }

      // change a future-with-exception into a future-with-value
      // where the value is a ProcessFailure
      resultFuture recover {
        case NonFatal(e) =>
          ProcessFailure(e)
      }
    }
  }

  def onApplicationStop() = {
    Logger.debug("AppManager onApplicationStop is disabled pending some refactoring so it works with FakeApplication in tests")
    //Logger.debug("Killing app cache actor onApplicationStop")
    //appCache ! PoisonPill
  }
}
