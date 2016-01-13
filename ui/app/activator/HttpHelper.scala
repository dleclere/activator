/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */
package activator

import play.api.libs.ws.{ WSAuthScheme, DefaultWSResponseHeaders, WSRequestHolder }

import scala.concurrent.Future
import scala.util.{ Failure, Success }
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee._
import java.io._
import play.api.http.{ ContentTypeOf, Writeable }

object HttpHelper {
  // Shameless Rx hack
  trait Observer[T] {
    def onCompleted(): Unit
    def onError(error: Throwable): Unit
    def onNext(data: T): Unit
  }

  case class ChunkData(contentLength: Option[Int],
    chunkSize: Int,
    total: Int)

  trait ProgressObserver extends Observer[ChunkData]

  val devNullBuilder: ProgressObserver = new ProgressObserver {
    def onCompleted(): Unit = ()
    def onError(error: Throwable): Unit = ()
    def onNext(data: ChunkData): Unit = ()
  }

  val printProgressBuilder: ProgressObserver = new ProgressObserver {
    private var seenBytes: Int = 0
    private var expectedBytes: Option[Int] = None

    def onCompleted(): Unit =
      expectedBytes match {
        case None =>
          println(s"DONE!! total: $seenBytes")
        case Some(cl) =>
          println(s"DONE !! expected: $cl -- percent: ${(seenBytes.toDouble / cl.toDouble) * 100.0} -- total: $seenBytes")
      }

    def onError(error: Throwable): Unit =
      println(s"Error: $error")

    def onNext(data: ChunkData): Unit = {
      seenBytes = data.total
      expectedBytes = data.contentLength
      expectedBytes match {
        case None =>
          println(s"chunk: ${data.chunkSize} -- total: ${data.total}")
        case Some(cl) =>
          println(s"expected: $cl -- percent: ${(data.total.toDouble / cl.toDouble) * 100.0} -- chunk: ${data.chunkSize} -- total: ${data.total}")
      }
    }
  }

  private def step(
    expectedBytes: Option[Int],
    destination: File,
    outputStream: FileOutputStream,
    progressObserver: ProgressObserver): Input[Array[Byte]] => Iteratee[Array[Byte], File] = {
    def innerStep(total: Int): Input[Array[Byte]] => Iteratee[Array[Byte], File] = {
      case Input.Empty => Cont(innerStep(total))
      case Input.EOF =>
        progressObserver.onCompleted()
        Done(destination, Input.EOF)
      case Input.El(e) =>
        val newTotal = total + e.size
        progressObserver.onNext(ChunkData(expectedBytes, e.size, newTotal))
        outputStream.write(e)
        Cont(innerStep(newTotal))
    }

    innerStep(0)
  }

  def identityHolder(holder: WSRequestHolder): WSRequestHolder = holder

  def proxyHolder(holder: WSRequestHolder): WSRequestHolder = (sys.props.get("http.proxyUser"), sys.props.get("http.proxyPassword")) match {
    case (Some(u), Some(p)) => holder.withAuth(u, p, WSAuthScheme.BASIC) // <- Only viable option?
    case _ => holder
  }

  def doGet(
    destination: File,
    outputStream: FileOutputStream,
    holder: WSRequestHolder,
    observer: ProgressObserver): Future[Iteratee[Array[Byte], File]] = {
    proxyHolder(holder).get {
      case DefaultWSResponseHeaders(200, rh) =>
        val contentLength = rh.get(play.api.http.HeaderNames.CONTENT_LENGTH).flatMap(_.headOption.map(_.toInt))
        Cont(step(contentLength, destination, outputStream, observer))
      case DefaultWSResponseHeaders(x, _) => throw new RuntimeException(s"non-200 response code: $x for request ${holder.url}")
    }
  }

  def doPost[T](body: T)(
    destination: File,
    outputStream: FileOutputStream,
    holder: WSRequestHolder,
    observer: ProgressObserver)(implicit wrt: Writeable[T], ct: ContentTypeOf[T]): Future[Iteratee[Array[Byte], File]] = {
    proxyHolder(holder).postAndRetrieveStream(body) {
      case DefaultWSResponseHeaders(200, rh) =>
        val contentLength = rh.get(play.api.http.HeaderNames.CONTENT_LENGTH).flatMap(_.headOption.map(_.toInt))
        Cont(step(contentLength, destination, outputStream, observer))
      case DefaultWSResponseHeaders(x, _) => throw new RuntimeException(s"non-200 response code: $x for request ${holder.url}")
    }
  }

  def retrieveFileHttp(
    holder: WSRequestHolder,
    observer: ProgressObserver,
    destination: File = File.createTempFile("activator_", ".tmp"),
    executor: (File, FileOutputStream, WSRequestHolder, ProgressObserver) => Future[Iteratee[Array[Byte], File]] = doGet,
    timeout: akka.util.Timeout = Akka.longTimeoutThatIsAProblem): Future[File] = {
    // import com.ning.http.client.Realm.AuthScheme
    val outputStream = new FileOutputStream(destination)

    val iterateeFuture = executor(destination, outputStream, holder.withRequestTimeout(timeout.duration.toMillis.intValue), observer) flatMap (_.run)
    iterateeFuture onComplete {
      case _: Success[File] => outputStream.close()
      case Failure(t) =>
        outputStream.close()
        destination.delete()
        observer.onError(t)
    }
    iterateeFuture
  }
}
