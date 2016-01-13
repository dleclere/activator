package activator.typesafeproxy

import akka.actor._
import org.junit.Assert._
import org.junit._

import scala.reflect.ClassTag
import scala.util.{ Failure, Success, Try }

object TypesafeComProxyTest {
  val authenticated = Success(AuthenticationStates.Authenticated(AuthenticationStates.emptyAuthentication))
  def failure(error: Throwable): Try[AuthenticationState] = Failure(error)
  def success(subscriberData: SubscriberData = SubscriberData.exampleDetail()): Try[SubscriberData] = Success(subscriberData)

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

class TypesafeComProxyTest extends DefaultSpecification {
  import TypesafeComProxy._
  import TypesafeComProxyTest._

  @Test
  def testShouldAuthenticate(): Unit = withHelper { helper =>
    import helper._
    withProxy() { proxy =>
      proxy ! Authentication.Get(testActor, testActor)
      val init = expectMsgType[FakeAuthenticatorInit]
      proxy ! Authentication.Put(authenticated, init.version, testActor)
      val rs = expectMsgAllClassOf(classOf[Authentication.Outcome], classOf[Authentication.Value])
      assertTrue(rs.find(_.isInstanceOf[Authentication.Outcome]).get.asInstanceOf[Authentication.Outcome].result.isSuccess)
      val v = rs.find(_.isInstanceOf[Authentication.Value]).get.asInstanceOf[Authentication.Value]
      assertTrue(v.value.isSuccess)
      assertTrue(s"v.version: ${v.version}, init.version: ${init.version}", v.version == (init.version + 1))
    }
  }

  @Test
  def testShouldFailToUpdate(): Unit = withHelper { helper =>
    import helper._
    withProxy() { proxy =>
      proxy ! Authentication.Get(testActor, testActor)
      val init = expectMsgType[FakeAuthenticatorInit]
      proxy ! Authentication.Put(authenticated, init.version + 1, testActor)
      val rs = expectMsgType[Authentication.Outcome]
      assertTrue(rs.result.isFailure)
    }
  }
}
