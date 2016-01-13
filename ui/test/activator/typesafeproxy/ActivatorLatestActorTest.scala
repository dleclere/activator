package activator.typesafeproxy

import akka.actor._
import org.junit.Assert._
import org.junit._

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }

object ActivatorLatestActorTest {

  val info = Success(ActivatorLatestInfo.exampleDetail())
  def failure(error: Throwable): Try[ActivatorLatestInfo] = Failure(error)

  val canceled = failure(new ProxyCanceled("canceled"))
  val failed = failure(new ProxyFailure("fail"))
  val invalid = failure(new ProxyInvalidCredentials("invalid"))
  val timeout = failure(new ProxyTimeout("timeout"))

  def assertTypeOf[T](x: Any)(implicit ct: ClassTag[T]): Unit =
    if (!ct.runtimeClass.isInstance(x)) throw new AssertionError(s"Value '$x' of type ${x.getClass.getName} is not of type ${ct.runtimeClass.getName}", null)

}

class ActivatorLatestActorTest extends DefaultSpecification {
  import ActivatorLatestActorTest._
  import TypesafeComProxy._

  @Test
  def testShouldSucceed(): Unit = withHelper { helper =>
    import helper._
    withActivatorLatestActor(activatorLatestResult(info)) { ia =>
      val put = expectMsgAllClassOf(classOf[ActivatorInfo.Put], classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction]).find(_.isInstanceOf[ActivatorInfo.Put]).map(_.asInstanceOf[ActivatorInfo.Put]).get
      assertTrue(put.value.isSuccess)
    }
  }

  @Test
  def testHandleTimeoutRetry(): Unit = withHelper { helper =>
    import helper._
    val (ref, func) = mutableActivatorLatestResult(timeout)
    withActivatorLatestActor(func) { ia =>
      expectMsgAllClassOf(classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction], classOf[UIActor.WebSocket.Failure])
      ref.set(info)
      ia ! UIActor.Retry
      val put = expectMsgAllClassOf(classOf[ActivatorInfo.Put], classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction]).find(_.isInstanceOf[ActivatorInfo.Put]).map(_.asInstanceOf[ActivatorInfo.Put]).get
      assertTrue(put.value.isSuccess)
    }
  }

  @Test
  def testHandleTimeoutCancel(): Unit = withHelper { helper =>
    import helper._
    val (ref, func) = mutableActivatorLatestResult(timeout)
    withActivatorLatestActor(func) { ia =>
      expectMsgAllClassOf(classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction], classOf[UIActor.WebSocket.Failure])
      ia ! UIActor.Cancel
      val failure = expectMsgType[ActivatorInfo.Put]
      assertTypeOf[ProxyCanceled](failure.value.failed.get)
    }
  }
}
