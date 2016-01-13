package test

import org.junit.Assert._
import org.junit._
import activator.RootConfigOps
import activator.AppConfig
import java.io.File
import scala.concurrent._
import scala.concurrent.duration._
import java.io.FileOutputStream

object TestRootConfig extends RootConfigOps {
  private def mkDir(f: File): File = {
    f.mkdirs()
    f.getCanonicalFile
  }

  private def mkDir(path: String): File =
    mkDir(new File(path))

  val scratchHome = mkDir("target/config-test-scratch")
  val configHome = mkDir(new File(scratchHome, "X.NEW"))
  val previousConfigHome = mkDir(new File(scratchHome, "X.OLD"))

  // has to be lazy because trait uses it to init
  override lazy val userConfigFile =
    new File(configHome, "config.json")
  override lazy val previousUserConfigFile =
    new File(previousConfigHome, "config.json")

  def ensureDirsExist(): Unit = {
    def ensureDir(d: File): Unit = {
      d.mkdirs()

      if (!d.exists() || !d.isDirectory())
        throw new Exception("failed to create " + d)
    }
    ensureDir(configHome)
    ensureDir(previousConfigHome)
  }
}

// these tests are all synchronized because they are testing
// behavior of global state (TestRootConfig.user).
class ConfigTest {

  @Before
  def beforeEachTest(): Unit = synchronized {
    TestRootConfig.userConfigFile.delete()
    TestRootConfig.previousUserConfigFile.delete()
    TestRootConfig.ensureDirsExist()
  }

  @Test
  def testUserConfig(): Unit = synchronized {
    val rewritten = TestRootConfig.rewriteUser { old =>
      val appList = if (old.applications.exists(_.location.getPath == "foo"))
        old.applications
      else
        AppConfig(new File("foo"), "id", createdTime = Some(1L), usedTime = Some(1L)) +: old.applications
      old.copy(applications = appList)
    }
    Await.ready(rewritten, 5.seconds)
    val c = TestRootConfig.user
    assertTrue("app 'foo' now in user config", c.applications.exists(_.location.getPath == "foo"))
  }

  def removeProjectName(): Unit = {
    val rewritten = TestRootConfig.rewriteUser { old =>
      val withNoName = old.applications
        .find(_.location.getPath == "foo")
        .getOrElse(AppConfig(new File("foo"), "id", createdTime = Some(1L), usedTime = Some(1L)))
        .copy(cachedName = None)

      val appList = withNoName +: old.applications.filter(_.location.getPath != "foo")
      old.copy(applications = appList)
    }
    Await.ready(rewritten, 5.seconds)
    val c = TestRootConfig.user
    assertTrue("app 'foo' now in user config with no name",
      c.applications.exists({ p => p.location.getPath == "foo" && p.cachedName.isEmpty }))
  }

  @Test
  def testAddingProjectName(): Unit = synchronized {
    removeProjectName()

    val rewritten = TestRootConfig.rewriteUser { old =>
      val withName = old.applications
        .find(_.location.getPath == "foo")
        .getOrElse(AppConfig(new File("foo"), "id", createdTime = Some(1L), usedTime = Some(1L)))
        .copy(cachedName = Some("Hello World"))

      val appList = withName +: old.applications.filter(_.location.getPath != "foo")
      old.copy(applications = appList)
    }
    Await.ready(rewritten, 5.seconds)
    val c = TestRootConfig.user
    assertTrue("app 'foo' now in user config with a name",
      c.applications.exists({ p => p.location.getPath == "foo" && p.cachedName == Some("Hello World") }))
  }

  @Test
  def testRecoveringFromBrokenFile(): Unit = synchronized {
    val file = TestRootConfig.userConfigFile
    try {
      file.delete()

      val stream = new FileOutputStream(file)
      stream.write("{ invalid json! ]".getBytes())
      stream.close()

      TestRootConfig.forceReload()

      val e = try {
        TestRootConfig.user
        throw new AssertionError("We expected to get an exception and not reach here (first time)")
      } catch {
        case e: Exception => e
      }

      assertTrue("got the expected exception on bad json", e.getMessage().contains("was expecting double"))

      // bad json is still there, so things should still fail...
      val e2 = try {
        TestRootConfig.user
        throw new AssertionError("We expected to get an exception and not reach here (second time)")
      } catch {
        case e: Exception => e
      }

      assertTrue("got the expected exception on bad json", e2.getMessage().contains("was expecting double"))

      // delete the file... should now load the file fine
      if (!file.delete())
        throw new AssertionError("failed to delete " + file.getAbsolutePath())

      try {
        assertTrue("loaded an empty config after recovering from corrupt one", TestRootConfig.user.applications.isEmpty)
      } catch {
        case e: Exception =>
          throw new AssertionError("should not have had an error loading empty config", e)
      }
    } finally {
      // to avoid weird failures on next run of the tests
      file.delete()
    }
  }

  @Test
  def testRecoveringFromEmptyJsonFile(): Unit = synchronized {
    val file = TestRootConfig.userConfigFile
    try {
      file.delete()

      val stream = new FileOutputStream(file)
      stream.write("{}".getBytes())
      stream.close()

      TestRootConfig.forceReload()

      val e = try {
        TestRootConfig.user
        throw new AssertionError("We expected to get an exception and not reach here (first time)")
      } catch {
        case e: Exception => e
      }

      assertTrue("got the expected exception on bad json (expecting error.path.missing, got '" + e.getMessage + "'", e.getMessage().contains("error.path.missing"))

      // bad json is still there, so things should still fail...
      val e2 = try {
        TestRootConfig.user
        throw new AssertionError("We expected to get an exception and not reach here (second time)")
      } catch {
        case e: Exception => e
      }

      assertTrue("got the expected exception on bad json (expecting error.path.missing, got '" + e.getMessage + "'", e.getMessage().contains("error.path.missing"))

      // delete the file... should now load the file fine
      if (!file.delete())
        throw new AssertionError("failed to delete " + file.getAbsolutePath())

      try {
        assertTrue("loaded an empty config after recovering from corrupt one", TestRootConfig.user.applications.isEmpty)
      } catch {
        case e: Exception =>
          throw new AssertionError("should not have had an error loading empty config", e)
      }
    } finally {
      // to avoid weird failures on next run of the tests
      file.delete()
    }
  }

  @Test
  def testRecoveringFromBrokenFileManyTimes(): Unit = synchronized {
    // this is intended to reveal a race that we were seeing intermittently
    for (_ <- 1 to 100)
      testRecoveringFromBrokenFile()
  }

  @Test
  def testUpgradingFromPrevious(): Unit = synchronized {
    import play.api.libs.json._
    import activator.RootConfig

    val oldFile = TestRootConfig.previousUserConfigFile
    val newFile = TestRootConfig.userConfigFile

    val sampleApp = AppConfig(location = new File("somewhere"), id = "someapp", cachedName = None,
      createdTime = Some(1L), usedTime = Some(1L))
    val sampleRoot = RootConfig(Seq(sampleApp))

    val oldJson = Json.toJson(sampleRoot)

    val stream = new FileOutputStream(oldFile)
    stream.write(Json.stringify(oldJson).getBytes("UTF-8"))
    stream.close()

    newFile.delete()

    TestRootConfig.forceReload()

    val newRoot = TestRootConfig.user

    assertEquals("loaded old config", sampleRoot, newRoot)
    assertTrue("new config file exists", newFile.exists)
  }

  @Test
  def testIgnorePreviousWhenCurrentPresent(): Unit = synchronized {
    import play.api.libs.json._
    import activator.RootConfig

    val oldFile = TestRootConfig.previousUserConfigFile
    val newFile = TestRootConfig.userConfigFile

    val sampleAppOld = AppConfig(location = new File("somewhere"), id = "someapp", cachedName = None,
      createdTime = Some(1L), usedTime = Some(1L))
    val sampleRootOld = RootConfig(Seq(sampleAppOld))
    val sampleAppNew = AppConfig(location = new File("somewhere2"), id = "someapp2", cachedName = None,
      createdTime = Some(1L), usedTime = Some(1L))
    val sampleRootNew = RootConfig(Seq(sampleAppNew))

    val oldJson = Json.toJson(sampleRootOld)
    val newJson = Json.toJson(sampleRootNew)

    def writeJson(f: File, json: JsValue): Unit = {
      val stream = new FileOutputStream(f)
      stream.write(Json.stringify(json).getBytes("UTF-8"))
      stream.close()
    }

    newFile.delete()
    oldFile.delete()

    writeJson(oldFile, oldJson)
    writeJson(newFile, newJson)

    TestRootConfig.forceReload()

    val newRoot = TestRootConfig.user

    assertEquals("loaded new config", sampleRootNew, newRoot)
    assertTrue("new config file exists", newFile.exists)
    assertTrue("old config file exists", oldFile.exists)
  }
}
