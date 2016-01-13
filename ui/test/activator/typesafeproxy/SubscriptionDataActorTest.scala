package activator.typesafeproxy

import org.junit.Assert._
import org.junit._

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }

object SubscriptionDataActorTest {

  def success(subscriberData: SubscriberData = SubscriberData.exampleDetail()): Try[SubscriberData] = Success(subscriberData)
  def failure(error: Throwable): Try[SubscriberData] = Failure(error)

  val canceled = failure(new ProxyCanceled("canceled"))
  val failed = failure(new ProxyFailure("fail"))
  val invalid = failure(new ProxyInvalidCredentials("invalid"))
  val timeout = failure(new ProxyTimeout("timeout"))

  def assertTypeOf[T](x: Any)(implicit ct: ClassTag[T]): Unit =
    if (!ct.runtimeClass.isInstance(x)) throw new AssertionError(s"Value '$x' of type ${x.getClass.getName} is not of type ${ct.runtimeClass.getName}", null)

}

class SubscriptionDataActorTest extends DefaultSpecification {
  import SubscriptionDataActorTest._
  import TypesafeComProxy._

  @Test
  def testShouldSucceed(): Unit = withHelper { helper =>
    import helper._
    val expected = success()
    withSubscriptionDataActor(subscriptionDataResult(expected)) { rpc =>
      val g = expectMsgType[Authentication.Get]
      g.withValue(AuthenticationActorTest.authenticated, 0L)
      val put = expectMsgAllClassOf(10.seconds, classOf[SubscriberDetail.Put], classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction]).find(_.isInstanceOf[SubscriberDetail.Put]).map(_.asInstanceOf[SubscriberDetail.Put]).get
      assertTrue(put.value.isSuccess)
    }
  }

  @Test
  def testHandleCanceledFetch(): Unit = withHelper { helper =>
    import helper._
    withSubscriptionDataActor(delayedSubscriptionDataResult(3.seconds)) { rpc =>
      val g = expectMsgType[Authentication.Get]
      g.withValue(AuthenticationActorTest.authenticated, 0L)
      expectMsgType[UIActor.WebSocket.ReportStartAction]
      rpc ! UIActor.Cancel
      val failure = expectMsgAllClassOf(10.seconds, classOf[SubscriberDetail.Put], classOf[UIActor.WebSocket.ReportEndAction]).find(_.isInstanceOf[SubscriberDetail.Put]).map(_.asInstanceOf[SubscriberDetail.Put]).get
      assertTypeOf[ProxyCanceled](failure.value.failed.get)
    }
  }

  @Test
  def testHandleTimeoutRetry(): Unit = withHelper { helper =>
    import helper._
    val expected = success()
    val (ref, func) = mutableSubscriptionDataResult(timeout)
    withSubscriptionDataActor(func) { rpc =>
      val g = expectMsgType[Authentication.Get]
      g.withValue(AuthenticationActorTest.authenticated, 0L)
      expectMsgAllClassOf(classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction], classOf[UIActor.WebSocket.Failure])
      ref.set(expected)
      rpc ! UIActor.Retry
      val put = expectMsgAllClassOf(10.seconds, classOf[SubscriberDetail.Put], classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction]).find(_.isInstanceOf[SubscriberDetail.Put]).map(_.asInstanceOf[SubscriberDetail.Put]).get
      assertTrue(put.value.isSuccess)
    }
  }

  @Test
  def testHandleTimeoutCancel(): Unit = withHelper { helper =>
    import helper._
    val (ref, func) = mutableSubscriptionDataResult(timeout)
    withSubscriptionDataActor(func) { rpc =>
      val g = expectMsgType[Authentication.Get]
      g.withValue(AuthenticationActorTest.authenticated, 0L)
      expectMsgAllClassOf(classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction], classOf[UIActor.WebSocket.Failure])
      rpc ! UIActor.Cancel
      val failure = expectMsgType[SubscriberDetail.Put]
      assertTypeOf[ProxyCanceled](failure.value.failed.get)
    }
  }

}
