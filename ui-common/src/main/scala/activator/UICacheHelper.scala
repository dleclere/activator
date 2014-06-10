/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package activator

import java.net.URI
import java.util.Properties
import java.io.{ FileInputStream, File }
import scala.collection.JavaConverters._
import activator.properties.ActivatorProperties
import activator.properties.ActivatorProperties.SCRIPT_NAME
import activator.cache._
import akka.actor.ActorRefFactory
import activator.cache.RemoteTemplateRepository
import activator.templates.repository.UriRemoteTemplateRepository
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.ActorContext
import akka.event.LoggingAdapter

// This helper constructs the template cache in the default CLI/UI location.
object UICacheHelper {

  // TODO - Config or ActiavtorProperties?
  lazy val config = ConfigFactory.load()

  def log(actorFactory: ActorRefFactory) = actorFactory match {
    case system: ActorSystem => system.log
    case context: ActorContext => context.system.log
    case whatever => throw new RuntimeException(s"don't know how to get log from $whatever")
  }

  val localCache = new File(ActivatorProperties.ACTIVATOR_TEMPLATE_CACHE)

  val localSeed = Option(ActivatorProperties.ACTIVATOR_TEMPLATE_LOCAL_REPO) map (new File(_)) filter (_.isDirectory)

  def makeDefaultCache(actorFactory: ActorRefFactory)(implicit timeout: akka.util.Timeout): TemplateCache = {
    val loggingAdapter = log(actorFactory)
    val privateRepos = RepositoriesConfig.getRepositories(loggingAdapter).
      map(rc => new UriRemoteTemplateRepository(rc.name, new URI(rc.uri), loggingAdapter))
    DefaultTemplateCache(
      actorFactory = actorFactory,
      location = localCache,
      remotes = Iterable(RemoteTemplateRepository(config, loggingAdapter)) ++ privateRepos,
      seedRepository = localSeed)
  }

  def makeLocalOnlyCache(actorFactory: ActorRefFactory)(implicit timeout: akka.util.Timeout): TemplateCache = {
    DefaultTemplateCache(
      actorFactory = actorFactory,
      location = localCache,
      seedRepository = localSeed)
  }

  /** Grabs the additional script files we should clone with templates, if they are available in our environment. */
  def scriptFilesForCloning: Seq[(File, String)] = {
    def fileFor(loc: String, name: String): Option[(File, String)] = Option(loc) map (new File(_)) filter (_.exists) map (_ -> name)
    val batFile = fileFor(ActivatorProperties.ACTIVATOR_LAUNCHER_BAT, SCRIPT_NAME + ".bat")
    val jarFile = fileFor(ActivatorProperties.ACTIVATOR_LAUNCHER_JAR, ActivatorProperties.ACTIVATOR_LAUNCHER_JAR_NAME)
    val bashFile = fileFor(ActivatorProperties.ACTIVATOR_LAUNCHER_BASH, SCRIPT_NAME)
    Seq(batFile, jarFile, bashFile).flatten
  }

  case class RepositoryConfig(name: String, uri: String)

  object RepositoriesConfig {

    val repositoriesFile = new File(activator.properties.ActivatorProperties.ACTIVATOR_USER_REPOSITORIES_FILE).getAbsoluteFile

    def getRepositories(loggingAdapter: LoggingAdapter): Iterable[RepositoryConfig] = {
      loggingAdapter.info(s"Checking for repositories file [${repositoriesFile.getAbsolutePath}].")
      if (repositoriesFile.exists()) {
        loggingAdapter.info("private repositories found.")
        val input = new FileInputStream(repositoriesFile)
        val repositories = new Properties()
        try {
          repositories.load(input)
        } finally {
          input.close()
        }

        repositories.asScala.map { entry =>
          val repo = RepositoryConfig(entry._1, entry._2)
          loggingAdapter.info(s"Found private repo [$repo].")
          repo
        }
      } else {
        loggingAdapter.info("No private repositories found.")
        Iterable.empty
      }
    }
  }

}
