/**
 *   Copyright (C) 2012 Typesafe Inc. <http://typesafe.com>
 */
package test

import org.junit.Assert._
import org.junit._
import java.io.File
import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import akka.pattern._
import activator.SetFilesToWatch
import akka.util.Timeout

class FileWatcherTest {

  val testUtil = new com.typesafe.sbtrc.TestUtil(scratchDir = new File("ui/target/scratch"))

  import testUtil._

  @Test
  def testFileWatcher() {
    val dir = makeDummySbtProject("fileWatching")

    val source = new File(dir, "src/main/scala/hello.scala")

    assertTrue("source file exists", source.exists())

    val system = ActorSystem("fileWatch")

    try {
      val watcher = system.actorOf(Props(new activator.FileWatcher))

      val observer = system.actorOf(Props(new Actor() with ActorLogging {
        watcher ! activator.SubscribeFileChanges(self)
        watcher ! SetFilesToWatch(Set(source))

        var changeObserver: Option[ActorRef] = None

        override def receive = {
          case "change-and-tell-me" =>
            val old = source.lastModified()
            // filesystem may not store resolution finer than seconds
            // (does not on Linux)
            source.setLastModified(old + 1000)
            changeObserver = Some(sender)
          case activator.FilesChanged(ref) =>
            changeObserver.foreach(_ ! "changed")
        }
      }));

      implicit val timeout = Timeout(5.seconds)
      implicit val ec = system.dispatcher

      val result1 = Await.result(observer ? "change-and-tell-me", timeout.duration)

      assertEquals("changed", result1)

      val result2 = Await.result(observer ? "change-and-tell-me", timeout.duration)

      assertEquals("changed", result2)

    } finally {
      system.shutdown();
      system.awaitTermination();
    }
  }
}
