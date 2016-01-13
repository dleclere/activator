package activator

import org.junit._
import org.junit.Assert._
import java.io.File
import activator.properties.ActivatorProperties
import java.util.concurrent.TimeUnit

class RepositoryConfigTest {

  var tmpActivatorHome: File = null

  @Before
  def setup(): Unit = {
    tmpActivatorHome = sbt.IO.createTemporaryDirectory
    System.setProperty("activator.home", tmpActivatorHome.getPath)
    // so it looks like we have an offline repo
    (new File(tmpActivatorHome, "repository")).mkdir()
  }

  @After
  def teardown(): Unit = {
    sbt.IO.delete(tmpActivatorHome)
    System.clearProperty("activator.home")
  }

  private def withConfigFilename[T](f: File => T): T = {
    sbt.IO.withTemporaryDirectory { dir =>
      f(new File(dir, "repositories"))
    }
  }

  private def withExistingConfig[T](s: String)(f: File => T): T = {
    withConfigFilename { file =>
      sbt.IO.write(file, s)
      f(file)
    }
  }

  private def checkContent(file: File, expected: String): Unit = {
    val content = sbt.IO.read(file)
    sbt.IO.write(new File("/tmp/content.txt"), content)
    sbt.IO.write(new File("/tmp/expected.txt"), expected)
    assertEquals(expected, content)
  }

  @Test
  def createConfigWhenNone(): Unit = {
    val expectedContent = """[repositories]
  local
  activator-launcher-local: file://${activator.local.repository-${activator.home-${user.home}/.activator}/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  activator-local: file://${activator.local.repository-""" + ActivatorProperties.ACTIVATOR_HOME() + """/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  maven-central
  typesafe-releases: http://repo.typesafe.com/typesafe/releases
  typesafe-ivy-releasez: http://repo.typesafe.com/typesafe/ivy-releases, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]

"""

    withConfigFilename { file =>
      assertFalse(s"$file doesn't exist yet", file.exists)
      RepositoryConfig.configureUserRepositories(file)
      checkContent(file, expectedContent)
    }
  }

  @Test
  def doNothingWhenWeAlreadyDidIt(): Unit = {
    val existingContent = """[scala]
  version: ${sbt.scala.version-auto}

[app]
  org: com.typesafe.activator
  name: activator-launcher
  version: ${activator.version-read(activator.version)[%s]}
  class: activator.ActivatorLauncher
  cross-versioned: false
  components: xsbti

[repositories]
  local
  activator-launcher-local: file://${activator.local.repository-${activator.home-${user.home}/.activator}/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  activator-local: file://${activator.local.repository-""" + ActivatorProperties.ACTIVATOR_HOME() + """/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  maven-central
  typesafe-releases: http://repo.typesafe.com/typesafe/releases
  typesafe-ivy-releasez: http://repo.typesafe.com/typesafe/ivy-releases, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]

[boot]
 directory: ${sbt.boot.directory-${sbt.global.base-${user.home}/.sbt}/boot/}
 properties: ${activator.boot.properties-${user.home}/.activator/version-${activator.launcher.generation-%d}.properties}

[ivy]
  ivy-home: ${user.home}/.ivy2
  checksums: ${sbt.checksums-sha1,md5}
  override-build-repos: ${sbt.override.build.repos-false}
  repository-config: ${sbt.repository.config-${sbt.global.base-${user.home}/.sbt}/repositories}

"""
    withExistingConfig(existingContent) { configFile =>
      assertTrue(s"$configFile exists", configFile.exists)
      val oldModTime = configFile.lastModified()
      Thread.sleep(1100)
      RepositoryConfig.configureUserRepositories(configFile)
      checkContent(configFile, existingContent)
      assertEquals("file timestamp unchanged", oldModTime, configFile.lastModified())
    }
  }

  @Test
  def addMissingSection(): Unit = {
    val existingContent = """[scala]
  version: ${sbt.scala.version-auto}

[app]
  org: com.typesafe.activator
  name: activator-launcher
  version: ${activator.version-read(activator.version)[%s]}
  class: activator.ActivatorLauncher
  cross-versioned: false
  components: xsbti

[boot]
 directory: ${sbt.boot.directory-${sbt.global.base-${user.home}/.sbt}/boot/}
 properties: ${activator.boot.properties-${user.home}/.activator/version-${activator.launcher.generation-%d}.properties}

[ivy]
  ivy-home: ${user.home}/.ivy2
  checksums: ${sbt.checksums-sha1,md5}
  override-build-repos: ${sbt.override.build.repos-false}
  repository-config: ${sbt.repository.config-${sbt.global.base-${user.home}/.sbt}/repositories}
"""

    val expectedContent = """[scala]
  version: ${sbt.scala.version-auto}

[app]
  org: com.typesafe.activator
  name: activator-launcher
  version: ${activator.version-read(activator.version)[%s]}
  class: activator.ActivatorLauncher
  cross-versioned: false
  components: xsbti

[boot]
 directory: ${sbt.boot.directory-${sbt.global.base-${user.home}/.sbt}/boot/}
 properties: ${activator.boot.properties-${user.home}/.activator/version-${activator.launcher.generation-%d}.properties}

[ivy]
  ivy-home: ${user.home}/.ivy2
  checksums: ${sbt.checksums-sha1,md5}
  override-build-repos: ${sbt.override.build.repos-false}
  repository-config: ${sbt.repository.config-${sbt.global.base-${user.home}/.sbt}/repositories}

[repositories]
  local
  activator-launcher-local: file://${activator.local.repository-${activator.home-${user.home}/.activator}/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  activator-local: file://${activator.local.repository-""" + ActivatorProperties.ACTIVATOR_HOME() + """/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  maven-central
  typesafe-releases: http://repo.typesafe.com/typesafe/releases
  typesafe-ivy-releasez: http://repo.typesafe.com/typesafe/ivy-releases, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]

"""

    withExistingConfig(existingContent) { configFile =>
      assertTrue(s"$configFile exists", configFile.exists)
      val oldModTime = configFile.lastModified()
      Thread.sleep(1100)
      RepositoryConfig.configureUserRepositories(configFile)
      checkContent(configFile, expectedContent)
      assertTrue("mod time should have changed", oldModTime != configFile.lastModified())
    }
  }

  @Test
  def addMissingActivatorLocalLine(): Unit = {
    val existingContent = """[repositories]
  local
  maven-central
  typesafe-releases: http://repo.typesafe.com/typesafe/releases
  typesafe-ivy-releasez: http://repo.typesafe.com/typesafe/ivy-releases, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]

"""

    val expectedContent = """[repositories]
  local
  activator-launcher-local: file://${activator.local.repository-${activator.home-${user.home}/.activator}/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  activator-local: file://${activator.local.repository-""" + ActivatorProperties.ACTIVATOR_HOME() + """/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  maven-central
  typesafe-releases: http://repo.typesafe.com/typesafe/releases
  typesafe-ivy-releasez: http://repo.typesafe.com/typesafe/ivy-releases, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]

"""

    withExistingConfig(existingContent) { configFile =>
      assertTrue(s"$configFile exists", configFile.exists)
      RepositoryConfig.configureUserRepositories(configFile)
      checkContent(configFile, expectedContent)
    }
  }

  @Test
  def replaceExistingActivatorLocalLine(): Unit = {
    val existingContent = """[repositories]
  local
  maven-central
  typesafe-releases: http://repo.typesafe.com/typesafe/releases
  typesafe-ivy-releasez: http://repo.typesafe.com/typesafe/ivy-releases, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  activator-local: wrongthinginwrongplace
  activator-launcher-local: alsowrongalsowrongplace
"""

    val expectedContent = """[repositories]
  local
  activator-launcher-local: file://${activator.local.repository-${activator.home-${user.home}/.activator}/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  activator-local: file://${activator.local.repository-""" + ActivatorProperties.ACTIVATOR_HOME() + """/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
  maven-central
  typesafe-releases: http://repo.typesafe.com/typesafe/releases
  typesafe-ivy-releasez: http://repo.typesafe.com/typesafe/ivy-releases, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]

"""

    withExistingConfig(existingContent) { configFile =>
      assertTrue(s"$configFile exists", configFile.exists)
      RepositoryConfig.configureUserRepositories(configFile)
      checkContent(configFile, expectedContent)
    }
  }
}
