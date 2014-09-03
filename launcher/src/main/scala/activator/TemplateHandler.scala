/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */
package activator

import activator.cache.TemplateCache
import scala.concurrent.duration._
import scala.concurrent.Await
import java.util.concurrent.TimeoutException
import akka.actor.ActorSystem
import akka.util.Timeout
import sbt.complete.{ Parsers, Parser }
import activator.cache.TemplateMetadata
import scala.util.control.NonFatal

object TemplateHandler extends ActivatorCliHelper {
  def apply(): Int = {
    val metadata = downloadTemplates(UICacheHelper.makeDefaultCache(ActivatorCliHelper.system), ActivatorCliHelper.defaultDuration)
    printTemplateNames(metadata)
    1
  }

  def downloadTemplates(cache: TemplateCache, duration: FiniteDuration)(implicit timeout: Timeout): Iterable[TemplateMetadata] = {
    try {
      System.out.println()
      System.out.println("Fetching the latest list of templates...")
      System.out.println()
      Await.result(cache.metadata, duration)
    } catch {
      case e: TimeoutException =>
        // fall back to just using whatever we have in the local cache
        System.out.println()
        System.out.println("Could not fetch the updated list of templates.  Using the local cache.")
        System.out.println("Check your proxy settings or increase the timeout.  For more details see:\nhttp://typesafe.com/activator/docs")
        System.out.println()

        val localOnlyCache = UICacheHelper.makeLocalOnlyCache(ActorSystem("fallback"))
        Await.result(localOnlyCache.metadata, duration)
    }
  }

  def printTemplateNames(metadata: Iterable[TemplateMetadata]) {
    val (featured, unfeatured) = metadata.toSeq.partition(_.featured)
    val (featuredSeed, featuredNotSeed) = featured.partition(_.tags.contains("seed"))
    val (unfeaturedSeed, unfeaturedNotSeed) = unfeatured.partition(_.tags.contains("seed"))
    val sections = Seq("Featured Seed Templates" -> featuredSeed,
      "Featured Tutorial Templates" -> featuredNotSeed,
      "Other Seed Templates" -> unfeaturedSeed,
      "Other Tutorial Templates" -> unfeaturedNotSeed) map {
        case (title, list) => title -> list.sortBy(_.name.toLowerCase)
      }
    sections foreach {
      case (title, ts) if ts.nonEmpty =>
        println(s"${title}:")
        ts foreach { t =>
          println(s"  ${t.name}")
        }
        println("")
      case _ =>
    }
  }

  def getTemplateName(possible: Seq[String], suggested: Seq[String]): Option[String] = {
    val templateNameParser: Parser[String] = {
      import Parser._
      import Parsers._
      token(any.* map { _ mkString "" }, "<template name>").examples(possible.toSet, false)
    }

    val options = suggested.sorted.zipWithIndex map { case (v, i) => (i + 1) -> v }
    System.out.println("Browse the list of templates: http://typesafe.com/activator/templates")
    if (suggested.isEmpty) {
      System.out.println("Enter a template name (hit tab to see a list)")
    } else {
      System.out.println("Choose from these featured templates or enter a template name:")
      for {
        (i, name) <- options
      } System.out.println(s"  ${i}) ${name}")
      System.out.println("(hit tab to see a list of all templates)")
    }
    val enteredOption = readLine(templateNameParser) map (_.trim) filterNot (_.isEmpty)
    enteredOption map { entered =>
      try {
        val i = Integer.parseInt(entered)
        options.find(_._1 == i).map(_._2).getOrElse(entered)
      } catch {
        case NonFatal(e) =>
          entered
      }
    }
  }

  def findTemplate(metadata: Iterable[TemplateMetadata], tName: String): Option[TemplateMetadata] = {
    metadata.find(_.name == tName) match {
      case tm @ Some(_) => tm
      case None =>
        System.err.println(s"Could not find template with name: $tName")
        None
    }
  }

}
