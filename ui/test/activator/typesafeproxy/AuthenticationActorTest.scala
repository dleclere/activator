package activator.typesafeproxy

import akka.actor._
import org.junit.Assert._
import org.junit._

import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }

object AuthenticationActorTest {

  val authenticated = Success(AuthenticationStates.Authenticated(AuthenticationStates.emptyAuthentication))
  def failure(error: Throwable): Try[AuthenticationState] = Failure(error)

  val canceled = failure(new ProxyCanceled("canceled"))
  val failed = failure(new ProxyFailure("fail"))
  val invalid = failure(new ProxyInvalidCredentials("invalid"))
  val timeout = failure(new ProxyTimeout("timeout"))

  val username = "username"
  val password = "password"
  val credentials = UIActor.Credentials(username, password)

  def assertTypeOf[T](x: Any)(implicit ct: ClassTag[T]): Unit =
    if (!ct.runtimeClass.isInstance(x)) throw new AssertionError(s"Value '$x' of type ${x.getClass.getName} is not of type ${ct.runtimeClass.getName}", null)

}

class AuthenticationActorTest extends DefaultSpecification {
  import AuthenticationActorTest._
  import TypesafeComProxy._

  @Test
  def testShouldSucceed(): Unit = withHelper { helper =>
    import helper._
    withAuthenticationActor(authenticationResult(authenticated)) { auth =>
      expectMsgType[UIActor.WebSocket.RequestCredentials]
      auth ! credentials
      val put = expectMsgAllClassOf(classOf[Authentication.Put], classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction]).find(_.isInstanceOf[Authentication.Put]).map(_.asInstanceOf[Authentication.Put]).get
      assertTrue(put.value.isSuccess)
    }
  }

  @Test
  def testHandleCanceledCredentialsRequest(): Unit = withHelper { helper =>
    import helper._
    withAuthenticationActor(authenticationResult(authenticated)) { auth =>
      expectMsgType[UIActor.WebSocket.RequestCredentials]
      auth ! UIActor.Cancel
      val failure = expectMsgType[Authentication.Put]
      assertTypeOf[ProxyCanceled](failure.value.failed.get)
    }
  }

  @Test
  def testHandleCanceledAuthentication(): Unit = withHelper { helper =>
    import helper._
    withAuthenticationActor(delayedAuthenticationResult(3.seconds)) { auth =>
      expectMsgType[UIActor.WebSocket.RequestCredentials]
      auth ! credentials
      expectMsgType[UIActor.WebSocket.ReportStartAction]
      auth ! UIActor.Cancel
      val failure = expectMsgAllClassOf(classOf[Authentication.Put], classOf[UIActor.WebSocket.ReportEndAction]).find(_.isInstanceOf[Authentication.Put]).map(_.asInstanceOf[Authentication.Put]).get
      assertTypeOf[ProxyCanceled](failure.value.failed.get)
    }
  }

  @Test
  def testHandleInvalidCredentialsRetry(): Unit = withHelper { helper =>
    import helper._
    val (ref, func) = mutableAuthenticationResult(invalid)
    withAuthenticationActor(func) { auth =>
      expectMsgType[UIActor.WebSocket.RequestCredentials]
      auth ! credentials
      expectMsgAllClassOf(classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction], classOf[UIActor.WebSocket.Failure])
      auth ! UIActor.Retry
      expectMsgType[UIActor.WebSocket.RequestCredentials]
      ref.set(authenticated)
      auth ! credentials
      val put = expectMsgAllClassOf(classOf[Authentication.Put], classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction]).find(_.isInstanceOf[Authentication.Put]).map(_.asInstanceOf[Authentication.Put]).get
      assertTrue(put.value.isSuccess)
    }
  }

  @Test
  def testHandleInvalidCredentialsCancel(): Unit = withHelper { helper =>
    import helper._
    val (ref, func) = mutableAuthenticationResult(invalid)
    withAuthenticationActor(func) { auth =>
      expectMsgType[UIActor.WebSocket.RequestCredentials]
      auth ! credentials
      expectMsgAllClassOf(classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction], classOf[UIActor.WebSocket.Failure])
      auth ! UIActor.Cancel
      val failure = expectMsgType[Authentication.Put]
      assertTypeOf[ProxyCanceled](failure.value.failed.get)
    }
  }

  @Test
  def testHandleTimeoutRetry(): Unit = withHelper { helper =>
    import helper._
    val (ref, func) = mutableAuthenticationResult(timeout)
    withAuthenticationActor(func) { auth =>
      expectMsgType[UIActor.WebSocket.RequestCredentials]
      auth ! credentials
      expectMsgAllClassOf(classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction], classOf[UIActor.WebSocket.Failure])
      auth ! UIActor.Retry
      ref.set(authenticated)
      auth ! credentials
      val put = expectMsgAllClassOf(classOf[Authentication.Put], classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction]).find(_.isInstanceOf[Authentication.Put]).map(_.asInstanceOf[Authentication.Put]).get
      assertTrue(put.value.isSuccess)
    }
  }

  @Test
  def testHandleTimeoutCancel(): Unit = withHelper { helper =>
    import helper._
    val (ref, func) = mutableAuthenticationResult(timeout)
    withAuthenticationActor(func) { auth =>
      expectMsgType[UIActor.WebSocket.RequestCredentials]
      auth ! credentials
      expectMsgAllClassOf(classOf[UIActor.WebSocket.ReportStartAction], classOf[UIActor.WebSocket.ReportEndAction], classOf[UIActor.WebSocket.Failure])
      auth ! UIActor.Cancel
      val failure = expectMsgType[Authentication.Put]
      assertTypeOf[ProxyCanceled](failure.value.failed.get)
    }
  }
}
