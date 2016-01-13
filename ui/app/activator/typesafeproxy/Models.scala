package activator.typesafeproxy

import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Json._

sealed trait SubscriptionLevel {
  def name: String
}
object SubscriptionLevels {
  sealed abstract class NamedSubscription(override val name: String) extends SubscriptionLevel
  case object Developer extends NamedSubscription("developerSubscriber")
  case object Production extends NamedSubscription("productionSubscriber")
  case object TwentyFourSeven extends NamedSubscription("twentyFourSevenSubscriber")

  val subscriptionLevelReads: Reads[SubscriptionLevel] = new Reads[SubscriptionLevel] {
    def reads(in: JsValue): JsResult[SubscriptionLevel] = in match {
      case JsString(Developer.name) => JsSuccess(Developer)
      case JsString(Production.name) => JsSuccess(Production)
      case JsString(TwentyFourSeven.name) => JsSuccess(TwentyFourSeven)
      case v => JsError(s"Expected one of 'developerSubscriber', 'productionSubscriber', or 'twentyFourSevenSubscriber' got: $v")
    }
  }

  val subscriptionLevelWrites: Writes[SubscriptionLevel] = new Writes[SubscriptionLevel] {
    def writes(in: SubscriptionLevel): JsValue = JsString(in.name)
  }

  implicit val subscriptionLevelFormat: Format[SubscriptionLevel] = Format[SubscriptionLevel](subscriptionLevelReads, subscriptionLevelWrites)

}

sealed trait SubscriberData
object SubscriberData {
  import SubscriptionLevels._

  case class NotASubscriber(message: String) extends SubscriberData
  case class Detail(id: String, subscription: SubscriptionLevel, isPaidSubscriber: Boolean, acceptedDate: Option[DateTime], majorVersion: String, currentReleaseVersion: String) extends SubscriberData

  private val exampleId = "id"
  private val exampleMajorVersion = "majorVersion"
  private val exampleCurrentReleaseVersion = "currentReleaseVersion"

  def exampleDetail(id: String = exampleId,
    subscription: SubscriptionLevel = SubscriptionLevels.Developer,
    isPaidSubscriber: Boolean = false,
    acceptedDate: Option[DateTime] = None,
    majorVersion: String = exampleMajorVersion,
    currentReleaseVersion: String = exampleCurrentReleaseVersion): Detail =
    Detail(id, subscription, isPaidSubscriber, acceptedDate, majorVersion, currentReleaseVersion)

  private val exampleMessage = "message"

  def exampleNotASubscriber(message: String = exampleMessage): NotASubscriber =
    NotASubscriber(message)

  val subscriberDataReads: Reads[SubscriberData] = new Reads[SubscriberData] {
    def reads(in: JsValue): JsResult[SubscriberData] = {
      val id = Json.fromJson[String](in \ "id").asOpt
      val subscription = Json.fromJson[Option[SubscriptionLevel]](in \ "primarySubscriberRole").asOpt
      val isPaidSubscriber = Json.fromJson[Boolean](in \ "isSubscriber").asOpt
      val acceptedDate = Json.fromJson[Option[DateTime]](in \ "acceptedDate").asOpt
      val majorVersion = Json.fromJson[String](in \ "majorVersion").asOpt
      val currentReleaseVersion = Json.fromJson[String](in \ "currentReleaseVersion").asOpt
      val message = Json.fromJson[Option[String]](in \ "message").asOpt

      (id, subscription, isPaidSubscriber, acceptedDate, majorVersion, currentReleaseVersion, message) match {
        case (_, Some(None), _, _, _, _, Some(Some(m))) => JsSuccess(NotASubscriber(m))
        case (_, Some(None), _, _, _, _, Some(None)) => JsSuccess(NotASubscriber("User not a subscriber"))
        case (Some(id), Some(Some(s)), Some(ips), Some(ad), Some(mv), Some(crv), _) => JsSuccess(Detail(id, s, ips, ad, mv, crv))
        case _ => JsError(s"'$in' could not be parsed into subscriber data")
      }
    }
  }

  val subscriberDataWrites: Writes[SubscriberData] = new Writes[SubscriberData] {
    def writes(in: SubscriberData): JsValue = in match {
      case NotASubscriber(m) => Json.obj("message" -> m)
      case Detail(id, s, ips, ad, mv, crv) => Json.obj("id" -> id,
        "primarySubscriberRole" -> s,
        "isPaidSubscriber" -> ips,
        "acceptedDate" -> ad,
        "majorVersion" -> mv,
        "currentReleaseVersion" -> crv)
    }
  }

  implicit val subscriberDataFormat: Format[SubscriberData] = Format[SubscriberData](subscriberDataReads, subscriberDataWrites)

}

case class ActivatorLatestInfo(url: String,
  miniUrl: String,
  version: String,
  size: String,
  miniSize: String,
  playVersion: String,
  akkaVersion: String,
  scalaVersion: String,
  launcherGeneration: Int)
object ActivatorLatestInfo {

  private val exampleUrl = "http://downloads.typesafe.com/typesafe-activator/1.3.2/typesafe-activator-1.3.2.zip"
  private val exampleMiniUrl = "http://downloads.typesafe.com/typesafe-activator/1.3.2/typesafe-activator-1.3.2-minimal.zip"
  private val exampleVersion = "1.3.2"
  private val exampleSize = "408M"
  private val exampleMiniSize = "1M"
  private val examplePlayVersion = "2.4.0"
  private val exampleAkkaVersion = "2.3.9"
  private val exampleScalaVersion = "2.11.5"
  private val exampleLauncherGeneration = 1

  def exampleDetail(url: String = exampleUrl,
    miniUrl: String = exampleMiniUrl,
    version: String = exampleVersion,
    size: String = exampleSize,
    miniSize: String = exampleMiniSize,
    playVersion: String = examplePlayVersion,
    akkaVersion: String = exampleAkkaVersion,
    scalaVersion: String = exampleScalaVersion,
    launcherGeneration: Int = exampleLauncherGeneration): ActivatorLatestInfo =
    ActivatorLatestInfo(url,
      miniUrl,
      version,
      size,
      miniSize,
      playVersion,
      akkaVersion,
      scalaVersion,
      launcherGeneration)

  val activatorLatestInfoReads: Reads[ActivatorLatestInfo] = Json.reads[ActivatorLatestInfo]

  val activatorLatestInfoWrites: Writes[ActivatorLatestInfo] = Json.writes[ActivatorLatestInfo]

  implicit val activatorLatestInfoFormat: Format[ActivatorLatestInfo] = Format[ActivatorLatestInfo](activatorLatestInfoReads, activatorLatestInfoWrites)

}
