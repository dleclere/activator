/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package console
package handler

import akka.actor.{ ActorRef, Props }
import activator.analytics.data.{ PlayRequestSummary, TimeRange, Scope }
import com.typesafe.trace.uuid.UUID
import console.handler.rest.PlayRequestJsonBuilder.PlayRequestResult
import com.typesafe.trace.{ ActorAnnotation, ActorInfo }
import scala.concurrent.{ ExecutionContext, Future }
import ExecutionContext.Implicits.global
import console.handler.rest.PlayRequestJsonBuilder
import console.AnalyticsRepository

object PlayRequestHandler {
  def props(repository: AnalyticsRepository,
    builderProps: Props = PlayRequestJsonBuilder.props()): Props =
    Props(classOf[PlayRequestHandler], repository, builderProps)

  case class PlayRequestModuleInfo(
    scope: Scope,
    modifiers: ScopeModifiers,
    time: TimeRange,
    dataFrom: Option[Long],
    traceId: Option[String]) extends ScopedModuleInformationBase
}

trait PlayRequestHandlerBase extends RequestHandler[PlayRequestHandler.PlayRequestModuleInfo] {
  import PlayRequestHandler._

  def usePlayRequestStats(sender: ActorRef, traceId: String, stats: Option[PlayRequestSummary], actorInfo: Set[ActorInfo]): Unit

  def onModuleInformation(sender: ActorRef, mi: PlayRequestModuleInfo): Unit = {
    mi.traceId match {
      case Some(id) =>
        val playStatsPromise = Future { repository.playRequestSummaryRepository.find(new UUID(id)) }
        val actorInfoPromise = Future {
          repository.traceRepository.trace(new UUID(id)).view
            .map(_.annotation)
            .filter(_.isInstanceOf[ActorAnnotation])
            .map(_.asInstanceOf[ActorAnnotation].info)
            .toSet
        }
        for {
          playStats <- playStatsPromise
          actorInfo <- actorInfoPromise
        } yield {
          usePlayRequestStats(sender, id, playStats, actorInfo)
        }
      case None => log.warning("Cannot call Play request detail information without proper trace id.")
    }
  }
}

class PlayRequestHandler(val repository: AnalyticsRepository,
  builderProps: Props) extends PlayRequestHandlerBase {
  val builder = context.actorOf(builderProps, "playRequestBuilder")

  def usePlayRequestStats(sender: ActorRef, traceId: String, stats: Option[PlayRequestSummary], actorInfo: Set[ActorInfo]): Unit = stats match {
    case Some(s) => builder ! PlayRequestResult(sender, s, actorInfo)
    case None => log.warning("Could not find Play request detail information with provided trace id %s", traceId)
  }
}
