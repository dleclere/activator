/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package console

import scala.language.implicitConversions
import akka.actor.{ ActorRef, Props, ActorLogging, Actor }
import console.handler._
import play.api.libs.iteratee.Concurrent
import play.api.libs.json._
import scala.collection.Seq
import activator.analytics.data.{ TimeRange, Scope }
import activator.analytics.rest.http.SortingHelpers.{ Descending, Ascending, SortDirection }
import akka.event.LoggingAdapter
import com.typesafe.trace.uuid.UUID
import activator.OutgoingMessage

trait ClientHandlerBase extends Actor with ActorLogging with ClientModuleHandler with RequestHelpers {
  import ClientController._

  def jsonHandlerProps: Props
  def overviewHandlerProps: Props
  def actorsHandlerProps: Props
  def actorHandlerProps: Props
  def playRequestsHandlerProps: Props
  def playRequestHandlerProps: Props
  def deviationsHandlerProps: Props
  def deviationHandlerProps: Props
  def lifecycleHandlerProps: Props

  val (enum, channel) = Concurrent.broadcast[JsValue]

  val jsonHandler = context.actorOf(jsonHandlerProps, "jsonHandler")
  val overviewHandler = context.actorOf(overviewHandlerProps, "overviewHandler")
  val actorsHandler = context.actorOf(actorsHandlerProps, "actorsHandler")
  val actorHandler = context.actorOf(actorHandlerProps, "actorHandler")
  val playRequestsHandler = context.actorOf(playRequestsHandlerProps, "playRequestsHandler")
  val playRequestHandler = context.actorOf(playRequestHandlerProps, "playRequestHandler")
  val deviationsHandler = context.actorOf(deviationsHandlerProps, "deviationsHandler")
  val deviationHandler = context.actorOf(deviationHandlerProps, "deviationHandler")
  val lifecycleHandler = context.actorOf(lifecycleHandlerProps, "lifecycleHandler")

  def onOverviewRequest(in: OverviewHandler.OverviewModuleInfo): Unit = overviewHandler ! in
  def onActorsRequest(in: ActorsHandler.ActorsModuleInfo): Unit = actorsHandler ! in
  def onActorRequest(in: ActorHandler.ActorModuleInfo): Unit = actorHandler ! in
  def onPlayRequestsRequest(in: PlayRequestsHandler.PlayRequestsModuleInfo): Unit = playRequestsHandler ! in
  def onPlayRequestRequest(in: PlayRequestHandler.PlayRequestModuleInfo): Unit = playRequestHandler ! in
  def onDeviationsRequest(in: DeviationsHandler.DeviationsModuleInfo): Unit = deviationsHandler ! in
  def onDeviationRequest(in: DeviationHandler.DeviationModuleInfo): Unit = deviationHandler ! in
  def onLifecycleRequest(in: LifecycleHandler.LifecycleModuleInfo): Unit = lifecycleHandler ! in

  var producer: Option[ActorRef] = None

  def withHandlers(handlers: Seq[RawInformationBase]): Receive = {
    case Tick =>
      handlers filter {
        m => !ClientModuleHandler.oneTimeHandlers.contains(m.handler)
      } map {
        // Updates the user defined rolling time window based on the input to "now"
        m =>
          m match {
            case rmi: RawModuleInformation => rmi.copy(time = toTimeRange(rmi.originalTime, log))
            case x => x
          }
      } foreach callHandler
    case Update(js) => for (p <- producer) p ! OutgoingMessage(js)
    case r: HandleRequest => jsonHandler ! r
    case mi: RawInformationBase => callHandler(mi)
    case InitializeCommunication(id, producer) =>
      this.producer = Some(producer)
      sender ! InitializeCommunication(id, self)

    case RegisterModules(newHandlers) =>
      // Only module handlers should be registered -
      // commands should not because they will only be invoked once and should not affect the module handlers.
      val moduleHandlers = newHandlers.filterNot { _.isInstanceOf[RawCommandInformation] }
      if (moduleHandlers.nonEmpty)
        context.become(withHandlers(moduleHandlers))
      for { mi <- newHandlers } self ! mi
  }

  def receive: Receive = withHandlers(Seq.empty[RawInformationBase])
}

object ClientHandler {
  def derivedProps(repository: AnalyticsRepository,
    defaultLimit: Int): Props =
    props(jsonHandlerProps = JsonHandler.props(),
      overviewHandlerProps = OverviewHandler.props(repository, defaultLimit),
      actorsHandlerProps = ActorsHandler.props(repository, defaultLimit),
      actorHandlerProps = ActorHandler.props(repository),
      playRequestsHandlerProps = PlayRequestsHandler.props(repository, defaultLimit),
      playRequestHandlerProps = PlayRequestHandler.props(repository),
      deviationsHandlerProps = DeviationsHandler.props(repository, defaultLimit),
      deviationHandlerProps = DeviationHandler.props(repository),
      lifecycleHandlerProps = LifecycleHandler.props(repository))

  def props(jsonHandlerProps: Props,
    overviewHandlerProps: Props,
    actorsHandlerProps: Props,
    actorHandlerProps: Props,
    playRequestsHandlerProps: Props,
    playRequestHandlerProps: Props,
    deviationsHandlerProps: Props,
    deviationHandlerProps: Props,
    lifecycleHandlerProps: Props): Props =
    Props(classOf[ClientHandler],
      jsonHandlerProps,
      overviewHandlerProps,
      actorsHandlerProps,
      actorHandlerProps,
      playRequestsHandlerProps,
      playRequestHandlerProps,
      deviationsHandlerProps,
      deviationHandlerProps,
      lifecycleHandlerProps)
}

class ClientHandler(val jsonHandlerProps: Props,
  val overviewHandlerProps: Props,
  val actorsHandlerProps: Props,
  val actorHandlerProps: Props,
  val playRequestsHandlerProps: Props,
  val playRequestHandlerProps: Props,
  val deviationsHandlerProps: Props,
  val deviationHandlerProps: Props,
  val lifecycleHandlerProps: Props) extends ClientHandlerBase

class JsonHandler extends Actor with ActorLogging with RequestHelpers {
  import ClientController._
  import JsonHandler._

  def receive = {
    case HandleRequest(js) =>
      // The json we're interested in resides under "location" since we reuse the common structure used for sbt communication
      // It could be improved but since the WS structure is going to be overhauled I figured we can keep this crap for now... (famous last words)
      val unbakedJson = (js \ "location").as[JsValue]
      sender ! RegisterModules(parseRequest(unbakedJson, log))
  }
}

trait RequestHelpers { this: ActorLogging =>
  import JsonHandler._
  implicit val reader = innerModuleReads
  implicit val commands = commandReads

  def parseRequest(js: JsValue, log: LoggingAdapter): Seq[RawInformationBase] = {
    def parseCommands(commands: List[InnerModuleCommand]): Seq[RawInformationBase] =
      commands map { i =>
        ClientModuleHandler.fromString(i.name) match {
          case Some(m) =>
            RawCommandInformation(
              handler = m,
              command = i.command)
          case None => sys.error("Could not find requested command module: ${i.module}")
        }
      }

    val originalTime = (js \ "time" \ "rolling").asOpt[String]
    val time = toTimeRange(originalTime, log)

    def parseModules(modules: List[InnerModuleInformation]): Seq[RawInformationBase] =
      modules map { i =>
        ClientModuleHandler.fromString(i.name) match {
          case Some(m) => RawModuleInformation(
            handler = m,
            scope = toScope(i.scope, log),
            originalTime = originalTime,
            time = time,
            pagingInformation = i.pagingInformation,
            dataFrom = i.dataFrom,
            sortCommand = i.sortCommand,
            sortDirection = i.sortDirection,
            traceId = i.traceId,
            eventId = i.eventId)
          case None => sys.error(s"Could not find requested module: ${i.name}")
        }
      }

    val commands = (js \ "commands").asOpt[List[InnerModuleCommand]]
    val commandModules = parseCommands(commands.getOrElse(List.empty[InnerModuleCommand]))
    val modules = (js \ "modules").asOpt[List[InnerModuleInformation]]
    val informationModules = parseModules(modules.getOrElse(List.empty[InnerModuleInformation]))
    commandModules ++ informationModules
  }

  def toScope(i: InternalScope, log: LoggingAdapter): Scope =
    Scope(
      path = i.actorPath,
      tag = i.tag,
      node = i.node,
      dispatcher = i.dispatcher,
      actorSystem = i.actorSystem,
      playPattern = i.playPattern,
      playController = i.playController)

  def toTimeRange(rolling: Option[String], log: LoggingAdapter): TimeRange = rolling match {
    case RollingMinutePattern(value) => TimeRange.minuteRange(System.currentTimeMillis, value.toInt)
    case x =>
      log.debug("Can not use parsed time range (using default 20 minutes instead): %s", x)
      TimeRange.minuteRange(System.currentTimeMillis, 20)
  }
}

object JsonHandler {
  import play.api.libs.json._
  import play.api.libs.functional.syntax._
  import SortDirections._

  def props(): Props =
    Props(classOf[JsonHandler])

  implicit val uuidReads: Reads[UUID] = Reads {
    case JsString(s) => JsSuccess(new UUID(s))
    case x @ _ => JsError(s"invalid input value for UUID: $x")
  }

  implicit val scopeReads = (
    (__ \ "node").readNullable[String] and
    (__ \ "actorSystem").readNullable[String] and
    (__ \ "dispatcher").readNullable[String] and
    (__ \ "tag").readNullable[String] and
    (__ \ "actorPath").readNullable[String] and
    (__ \ "playPattern").readNullable[String] and
    (__ \ "playController").readNullable[String])(InternalScope)

  implicit val chunkRangeReads = (
    (__ \ "min").read[Int] and
    (__ \ "max").read[Int])(ChunkRange)

  implicit val pagingReads = (
    (__ \ "offset").read[Int] and
    (__ \ "limit").read[Int])(PagingInformation)

  implicit val innerModuleReads = (
    (__ \ "name").read[String] and
    (__ \ "traceId").readNullable[String] and
    (__ \ "eventId").readNullable[UUID] and
    (__ \ "paging").readNullable[PagingInformation] and
    (__ \ "sortCommand").readNullable[String] and
    (__ \ "sortDirection").readNullable[SortDirection] and
    (__ \ "dataFrom").readNullable[Long] and
    (__ \ "scope").read[InternalScope] and
    (__ \ "chunkRange").readNullable[ChunkRange])(InnerModuleInformation)

  implicit val commandReads = (
    (__ \ "module").read[String] and
    (__ \ "command").read[String])(InnerModuleCommand)

  final val RollingMinutePattern = """^.*rolling=([1-9][0-9]?)minute[s]?.*""".r
}

case class RegisterModules(moduleInformation: Seq[RawInformationBase])

case class ScopeModifiers(
  anonymous: Boolean = true,
  temporary: Boolean = true)

case class InternalScope(
  node: Option[String] = None,
  actorSystem: Option[String] = None,
  dispatcher: Option[String] = None,
  tag: Option[String] = None,
  actorPath: Option[String] = None,
  playPattern: Option[String] = None,
  playController: Option[String] = None) {
}

trait InnerModuleBase {
  def name: String
}

case class InnerModuleInformation(
  name: String,
  traceId: Option[String],
  eventId: Option[UUID],
  pagingInformation: Option[PagingInformation],
  sortCommand: Option[String],
  sortDirection: Option[SortDirection],
  dataFrom: Option[Long],
  scope: InternalScope,
  chunkRange: Option[ChunkRange]) extends InnerModuleBase {}

case class InnerModuleCommand(name: String, command: String) extends InnerModuleBase {}

trait ModuleInformationBase

trait ScopedModuleInformationBase extends ModuleInformationBase {
  def scope: Scope
  def modifiers: ScopeModifiers
  def time: TimeRange
  def dataFrom: Option[Long]
  def traceId: Option[String]
}

object SortDirections {
  implicit val reads: Reads[SortDirection] = Reads {
    case JsString(s) => JsSuccess(SortDirection(s))
    case x @ _ => JsError(s"invalid input value for sort direction: $x")
  }

  implicit class SortDirectionsHelpers(in: activator.analytics.rest.http.SortingHelpers.SortDirection) {
    def toLegacy: activator.analytics.data.Sorting.SortDirection = in match {
      case Ascending => activator.analytics.data.Sorting.ascendingSort
      case Descending => activator.analytics.data.Sorting.descendingSort
      case x @ _ => sys.error(s"Unable to convert $x to a legacy sort value")
    }
  }
}

trait MultiValueModuleInformation[S] extends ModuleInformationBase {
  def pagingInformation: Option[PagingInformation]
  def sortOn: S
  def sortDirection: SortDirection
}

trait RawInformationBase {
  def handler: ClientModuleHandler.Handler
}

case class RawModuleInformation(
  handler: ClientModuleHandler.Handler,
  scope: Scope,
  modifiers: ScopeModifiers = ScopeModifiers(),
  originalTime: Option[String],
  time: TimeRange,
  pagingInformation: Option[PagingInformation] = None,
  sortCommand: Option[String],
  sortDirection: Option[SortDirection],
  dataFrom: Option[Long] = None,
  traceId: Option[String] = None,
  eventId: Option[UUID] = None,
  chunkRange: Option[ChunkRange] = None) extends RawInformationBase {}

case class RawCommandInformation(
  handler: ClientModuleHandler.Handler,
  command: String) extends RawInformationBase {}

case class PagingInformation(offset: Int, limit: Int)

trait WithPaging[S <: MultiValueModuleInformation[_]] {
  def defaultLimit: Int
  lazy val defaultOffset: Int = 0

  def withPagingDefaults[B](moduleInformation: S, defaultLimit: Int = defaultLimit, defaultOffset: Int = defaultOffset)(body: (Int, Int) => B): B = {
    val (offset, limit) = moduleInformation.pagingInformation.map(x => (x.offset, x.limit)).getOrElse((defaultOffset, defaultLimit))
    body(offset, limit)
  }
}
