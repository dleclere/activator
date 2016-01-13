/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package console
package handler

import akka.actor.{ ActorRef, Props }
import activator.analytics.data.{ PlayStatsSort, PlayStatsSorts, PlayRequestSummary, TimeRange, Scope }
import activator.analytics.rest.http.SortingHelpers.SortDirection
import console.handler.rest.PlayRequestsJsonBuilder.PlayRequestsResult
import scala.language.existentials
import console.handler.rest.PlayRequestsJsonBuilder
import console.AnalyticsRepository

object PlayRequestsHandler {
  def props(repository: AnalyticsRepository,
    defaultLimit: Int,
    builderProps: Props = PlayRequestsJsonBuilder.props()): Props =
    Props(classOf[PlayRequestsHandler], repository, builderProps, defaultLimit)

  case class PlayRequestsModuleInfo(scope: Scope,
    modifiers: ScopeModifiers,
    time: TimeRange,
    pagingInformation: Option[PagingInformation],
    sortOn: PlayStatsSort[_],
    sortDirection: SortDirection,
    dataFrom: Option[Long],
    traceId: Option[String]) extends MultiValueModuleInformation[PlayStatsSort[_]]

  def extractSortOn(sortCommand: Option[String]): PlayStatsSort[_] = sortCommand match {
    case Some(sort) ⇒ sort match {
      case "path" => PlayStatsSorts.PathSort
      case "time" => PlayStatsSorts.TimeSort
      case "controller" => PlayStatsSorts.ControllerSort
      case "method" => PlayStatsSorts.MethodSort
      case "responseCode" => PlayStatsSorts.ResponseCodeSort
      case _ => PlayStatsSorts.InvocationTimeSort
    }
    case _ => PlayStatsSorts.TimeSort
  }
}

trait PlayRequestsHandlerBase extends RequestHandler[PlayRequestsHandler.PlayRequestsModuleInfo] {
  import PlayRequestsHandler._
  import SortDirections._

  def usePlayRequestStats(sender: ActorRef, stats: Seq[PlayRequestSummary]): Unit

  def onModuleInformation(sender: ActorRef, mi: PlayRequestsModuleInfo): Unit = {
    usePlayRequestStats(sender,
      repository.playRequestSummaryRepository.findRequestsWithinTimePeriod(
        mi.time.startTime,
        mi.time.endTime,
        (for { p <- mi.pagingInformation } yield p.offset).getOrElse(0),
        (for { p <- mi.pagingInformation } yield p.limit).getOrElse(50),
        mi.sortOn,
        mi.sortDirection.toLegacy))
  }
}

class PlayRequestsHandler(val repository: AnalyticsRepository,
  builderProps: Props,
  val defaultLimit: Int) extends PlayRequestsHandlerBase {
  val builder = context.actorOf(builderProps, "playRequestsBuilder")

  def usePlayRequestStats(sender: ActorRef, stats: Seq[PlayRequestSummary]): Unit = {
    builder ! PlayRequestsResult(sender, stats)
  }

}
