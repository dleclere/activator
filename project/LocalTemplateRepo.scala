import sbt._
import ActivatorBuild._
import Keys._


object LocalTemplateRepo {
  // TODO - We can probably move this to its own project, to more clearly delineate that the UI uses these
  // for local testing....
  val localTemplateCache = settingKey[File]("target directory for local template cache")
  val localTemplateCacheCreated = taskKey[File]("task which creates local template cache")
  val remoteTemplateCacheUris = settingKey[Array[RemoteTemplateRepo]]("base URIs to get template caches from")
  val localTemplateCacheHash = settingKey[String]("which index from the remote URI to seed the cache from")
  val latestTemplateCacheHashs = taskKey[Array[String]]("get the latest template cache hash from the remote URIs")
  val checkTemplateCacheHashs = taskKey[Array[String]]("throw if our configured template cache hash is not the latest, otherwise return the local (and latest) hash")
  val enableCheckTemplateCacheHash = settingKey[Boolean]("true to enable checking we have latest cache before we publish")

  def settings: Seq[Setting[_]] = Seq(
    localTemplateCache <<= target(_ / "template-cache"),
    localTemplateCacheCreated <<= (localTemplateCache, localTemplateCacheHash, Keys.fullClasspath in Runtime, remoteTemplateCacheUris, streams) map makeTemplateCache,
    scalaVersion := Dependencies.scalaVersion,
    libraryDependencies += Dependencies.templateCache,
    // TODO - Allow debug version for testing?
    remoteTemplateCacheUris := Array("typesafe" withUri "http://downloads.typesafe.com/typesafe-activator"),
    localTemplateCacheHash := "716acd0c4c29b0ba1faf8f146c05c64afc2635c4",
    latestTemplateCacheHashs := downloadLatestTemplateCacheHashs(remoteTemplateCacheUris.value, streams.value),
    checkTemplateCacheHashs := {
      if (enableCheckTemplateCacheHash.value)
        checkLatestTemplateCacheHash(localTemplateCacheHash.value, latestTemplateCacheHashs.value)
      else
        Array(localTemplateCacheHash.value)
    },
    enableCheckTemplateCacheHash := true
  )

  def invokeTemplateCacheRepoMakerMain(cl: ClassLoader, dir: File, uris: Array[RemoteTemplateRepo]): Unit =
    invokeMainFor(cl, "activator.templates.TemplateCacheSeedGenerator", uris.flatMap(uri => Array("-remote", uri.name, uri.uri)) ++ Array(dir.getAbsolutePath))

  private def makeClassLoaderFor(classpath: Keys.Classpath): java.net.URLClassLoader = {
    val jars = classpath map (_.data.toURL)
    new java.net.URLClassLoader(jars.toArray, null)
  }

  private def invokeMainFor(cl: ClassLoader, mainClass: String, args: Array[String]): Unit = {
    println("Loading " + mainClass + " from: " + cl)
    val maker = cl.loadClass(mainClass)
    println("Invoking object: " + maker)
    val mainMethod = maker.getMethod("main", classOf[Array[String]])
    println("Invoking maker: " + maker)
    mainMethod.invoke(null, args)
  }

  def makeTemplateCache(targetDir: File, hash: String, classpath: Keys.Classpath, uris: Array[RemoteTemplateRepo], streams: TaskStreams): File = {
    val cachePropsFile = targetDir / "cache.properties"

    // Delete stale cache.
    if (cachePropsFile.exists) {
      val oldHash = readHashFromProps(cachePropsFile)
      if (oldHash != hash) {
        streams.log.info(s"Deleting old template cache $oldHash to create new one $hash")
        IO.delete(targetDir)
      }
    }

    if (targetDir.exists) {
      streams.log.info(s"Template cache $hash appears to exist already")
    } else try {
      streams.log.info(s"Downloading template cache $hash")

      IO createDirectory targetDir

      // Important: Never _overwrite_ this file without
      // also deleting the index, because
      // activator-template-cache assumes the hash goes with
      // the index we have.
      IO.write(targetDir / "cache.properties", "cache.hash=" + hash + "\n")

      val cl = makeClassLoaderFor(classpath)
      // Akka requires this crazy
      val old = Thread.currentThread.getContextClassLoader
      Thread.currentThread.setContextClassLoader(cl)
      try invokeTemplateCacheRepoMakerMain(cl, targetDir, uris)
      finally Thread.currentThread.setContextClassLoader(old)
    } catch {
      case ex: Exception =>
         IO delete targetDir
         throw ex
    }
    targetDir
  }

  case class RemoteTemplateRepo(name: String, uri: String)

  implicit class RichRemoteTemplateRepo(val name: String) extends AnyVal {
    def withUri(uri: String): RemoteTemplateRepo = RemoteTemplateRepo(name, uri)
  }

  def readHashFromProps(propsFile: File): String = {
    val fis = new java.io.FileInputStream(propsFile)
    try {
      val props = new java.util.Properties
      props.load(fis)
      Option(props.getProperty("cache.hash")).getOrElse(sys.error(s"No cache.hash in ${propsFile}"))
    } finally {
      fis.close()
    }
  }

  // IO.download appears to use caching and we need the latest here
  def downloadWithoutCaching(url: URL, toFile: File): Unit = {
    import java.net.HttpURLConnection
    val connection = url.openConnection() match {
      case http: HttpURLConnection =>
        http.setUseCaches(false)
        http
      case whatever =>
        throw new Exception("Got weird non-http connection " + whatever)
    }
    if (connection.getResponseCode() != 200)
      sys.error(s"Response code ${connection.getResponseCode()} from ${url}")

    Using.bufferedInputStream(connection.getInputStream()) { in =>
      IO.transfer(in, toFile)
    }
  }

  def downloadLatestTemplateCacheHashs(uriStrings: Array[RemoteTemplateRepo], streams: TaskStreams): Array[String] = {
    uriStrings.map { uriString =>
      IO.withTemporaryDirectory { tmpDir =>
        // this is cut-and-pastey/hardcoded vs. activator-template-cache,
        // the main problem with that is that it uses http instead of the
        // S3 API and therefore gets stale cached content.
        val propsFile = tmpDir / "current.properties"
        val url = new URL(uriString + "/index/v2/current.properties")
        streams.log.info(s"Downloading ${url} to ${propsFile}")
        downloadWithoutCaching(url, propsFile)
        val hash = readHashFromProps(propsFile)
        streams.log.info(s"Got latest template cache hash $hash")
        hash
      }
    }
  }

  def checkLatestTemplateCacheHash(ourHash: String, latestHashs: Array[String]): Array[String] = {
    latestHashs.map { latestHash =>
      if (ourHash != latestHash)
        sys.error(s"The latest template index is ${latestHash} but our configured index is ${ourHash} (if you want to override this, `set LocalTemplateRepo.enableCheckTemplateCacheHash := false` perhaps)")
      else
        ourHash
    }
  }
}
