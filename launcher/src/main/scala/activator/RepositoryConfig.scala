/**
 * Copyright (C) 2015 Typesafe <http://typesafe.com/>
 */
package activator

import scala.util.control.NonFatal
import activator.properties.ActivatorProperties._
import java.io.File

// Here's where we specify our repositories configuration.  The
// goal is to add the offline repository that comes in our zip
// file to Activator itself AND to sbt server.
//
// Note: as with any comment this one can be wrong. This is how
// someone THOUGHT it worked at one point. Trust but verify!
//
// Important background:
//  - there's a default repository configuration embedded in the
//    launcher, which we generate in project/Packaging.scala
//  - the launcher also configures a repository-config, which
//    is an override file for the repositories section in the
//    launcher
//  - in this source file we are creating ~/.sbt/repositories
//  - if ~/.sbt/repositories exists, sbt will IGNORE the entire
//    embedded config in the launcher, not merge with it.
//    ~/.sbt/repositories entirely replaces the embedded config.
//    This is how the repository-config is set up in
//    Packaging.scala.
//  - activator.home is set to the activator/activator.bat wrapper
//    scripts' location
//  - ${activator.home}/repository would be our offline repo
//    for the "fat" zip
//  - in the "minimal" zip, ${activator.home}/repository doesn't
//    exist
//  - when the activator scripts are copied into an app,
//    ${activator.home}/repository doesn't exist
//  - activator.local.repository is a user-configurable override
//    that replaces ${activator.home}/repository (you would
//    specify this on the activator command line or in
//    ~/.sbt/jvmargs)//
// With that background, we are trying to handle these cases:
//  - if you've never run activator and run it the first time,
//    the launcher uses its embedded repository config, and then
//    creates ~/.sbt/repositories, which gets used by sbt server
//  - the second time you run activator, both launcher and sbt
//    server would use ~/.sbt/repositories
//  - if you upgrade to a new Activator (which probably moves
//    activator.home), the first time you run it, it uses
//    ~/.sbt/repositories which will configure both the previous
//    version of activator's repository and also the new version's
//    repository due to activator.home
//  - if you upgrade, the second time you run it we'll have edited
//    ~/.sbt/repositories and it will only use the new version's
//    repo
//
// We write two repos to ~/.sbt/repositories; one is hardcoded
// to the activator path when we created ~/.sbt/repositories,
// and this is intended to affect sbt server. The other honors
// activator.home which is set by the wrapper script, so this
// one is intended to work right with the Activator launcher
// (which may be a newer version).
//
// In the "average" case (the second and subsequent times you
// run the same version of Activator) the two repos should be
// the same directory.
//
// If the user has their own stuff in ~/.sbt/repositories we do
// our very best not to mess it up, we only change the lines
// that start with activator-.
//
// We also only edit ~/.sbt/repositories if we are a "fat" zip,
// that is if ${activator.home}/repository exists. If you run
// an activator from inside an app or from the minimal zip,
// we use the last "fat" repository you have used.
object RepositoryConfig {
  private val repositoriesSectionName = "repositories"

  private val isWindows = sys.props("os.name").toLowerCase.indexOf("win") >= 0

  // configure your per-user repos to have the offline
  // repo in activator.home, if possible.
  def configureUserRepositories(): Unit =
    // FIXME look at sbt.repository.config and possibly sbt.global.base
    configureUserRepositories(new File(new File(GLOBAL_USER_HOME), ".sbt/repositories"))

  // this overload exists for unit tests only
  def configureUserRepositories(repositoriesFile: java.io.File): Unit = try {
    // leave configuration alone if we don't have an offline repo, which
    // means we'll use the config embedded in the launcher by project/Packaging.scala
    if ((new java.io.File(ACTIVATOR_HOME, "repository")).exists)
      replaceSection(repositoriesFile, repositoriesSectionName, newRepositorySection _)
  } catch {
    case NonFatal(e) =>
      System.err.println(s"Configuring Activator offline repository failed: ${e.getMessage}")
  }

  private final val fileMatcher = "^file://*".r
  private final val fileScheme = "file://"

  private def quoteForFileURI(path: String): String = {
    val uriString = (new java.io.File(path)).toURI.toASCIIString()
    if (isWindows && fileMatcher.findFirstIn(uriString).isDefined)
      fileMatcher.replaceFirstIn(uriString, "")
    else if (!isWindows && uriString.startsWith(fileScheme))
      uriString.substring(fileScheme.length)
    else
      path // give up, hope for best?
  }

  private def replaceRepoLine(old: Section, afterName: String, repoName: String, replacementLine: String): Section = {
    val (before, after) =
      old.lines.filterNot(_.trim.startsWith(s"${repoName}:")).partition(name =>
        name.trim == afterName || name.trim.startsWith("${afterName}:"))
    val oldLine = old.lines.find(_.trim.startsWith(s"${repoName}:"))
    // this check is to avoid adding the comment if we haven't really changed anything
    if (oldLine != Some(replacementLine))
      old.copy(lines = before ++ Seq(replacementLine) ++ after)
    else
      old
  }

  // Under Windows `file://` is considered a UNC path.  Adding the extra '/' or '//'
  // solves the problem that the current user's authorization is sufficient
  // to access the target file.
  private lazy val fileMarker = if (isWindows) "file:////" else "file://"

  private def newRepositorySection(oldOption: Option[Section]): Section = {
    // this repo is primarily for sbt server - hardcoded activator.home
    // based on the most recent Activator to run.
    val activatorLocalLine = """  activator-local: """ + fileMarker + """${activator.local.repository-""" +
      quoteForFileURI(ACTIVATOR_HOME) +
      """/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]"""

    // this repo is for the launcher, which is run by a wrapper script
    // that sets activator.home. The launcher only uses its embedded repo
    // config if ~/.sbt/repositories doesn't exist.
    val activatorLauncherLine = """  activator-launcher-local: """ + fileMarker + """${activator.local.repository-${activator.home-${user.home}/.activator}/repository}, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]"""

    oldOption match {
      case Some(old) =>
        // if (!isWindows) {
        val withActivatorLocal =
          replaceRepoLine(old, "local", "activator-local", activatorLocalLine)
        replaceRepoLine(withActivatorLocal, "local", "activator-launcher-local", activatorLauncherLine)
      // } else stripRepoLines(old, Set("activator-local", "activator-launcher-local"))
      case None =>
        // create the entire repositories section
        val header = """  local
""" + activatorLauncherLine + """
""" + activatorLocalLine
        val allRepoLines = header + """
  maven-central
  typesafe-releases: http://repo.typesafe.com/typesafe/releases
  typesafe-ivy-releasez: http://repo.typesafe.com/typesafe/ivy-releases, [organization]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]"""

        Section(repositoriesSectionName, allRepoLines.split('\n').toVector, s"[$repositoriesSectionName]")
    }
  }

  // outputting the "rawLine" followed by "lines" for each section is supposed
  // to preserve the original file
  private case class Section(name: String, lines: Vector[String], rawLine: String) {
    def isStuffBeforeFirstSection: Boolean = name.isEmpty
  }
  private object Section {
    def stuffBeforeFirstSection = Section("", Vector.empty, "")
  }

  private def sectionSplit(reader: java.io.BufferedReader): Seq[Section] = {
    // sbt parser defines a "section" as a trimmed line that starts with '['.
    // if a line that starts with '[' isn't a section, it's an error.
    def accumulate(reversedSections: List[Section], currentSection: Section): List[Section] = {
      reader.readLine() match {
        case null =>
          (currentSection :: reversedSections).reverse
        case line =>
          val trimmed = line.trim()
          if (trimmed.nonEmpty && trimmed.charAt(0) == '[') {
            val i = trimmed.indexOf(']')
            // if there's no ']' sbt throws an error, but we don't want to barf on anything here.
            val name = trimmed.substring(1, if (i >= 0) i else trimmed.length)
            accumulate(currentSection :: reversedSections, Section(name, Vector.empty, line))
          } else {
            // use original, not trimmed line because we don't want to gratuitously reformat the file
            accumulate(reversedSections, currentSection.copy(lines = currentSection.lines :+ line))
          }
      }
    }
    accumulate(Nil, Section.stuffBeforeFirstSection)
  }

  private def writeSections(writer: java.io.BufferedWriter, sections: Seq[Section]): Unit = {
    sections foreach { section =>
      if (!section.isStuffBeforeFirstSection) {
        writer.append(section.rawLine)
        writer.newLine()
      }
      section.lines.foreach { l =>
        writer.append(l)
        writer.newLine()
      }
      // put a blank line after each section even if there wasn't before
      section.lines.lastOption foreach { lastLine =>
        if (lastLine.trim.nonEmpty) {
          writer.newLine()
        }
      }
    }
  }

  private def replaceSection(sections: Seq[Section], name: String, f: Option[Section] => Section): Seq[Section] = {
    if (sections.exists(_.name == name)) {
      sections map {
        case s if s.name == name =>
          f(Some(s))
        case s => s
      }
    } else {
      sections :+ f(None)
    }
  }

  private def replaceSection(file: java.io.File, name: String, f: Option[Section] => Section): Unit = {
    val reader = try new java.io.BufferedReader(new java.io.FileReader(file))
    catch {
      case _: java.io.IOException =>
        // pretend file was empty
        new java.io.BufferedReader(new java.io.StringReader(""))
    }
    val (newSections, oldSections) = try {
      val sections = sectionSplit(reader)
      (replaceSection(sections, name, f), sections)
    } finally {
      reader.close()
    }

    // check if we won't change anything because we don't want to gratuitously
    // do file IO that might break or alter timestamps or whatever
    if (newSections != oldSections) {
      sbt.IO.createViaTemporary(file) { file =>
        // this is half-ass vs. writing and atomically renaming a tmpfile,
        // but java makes that annoying cross-platform
        val writer = new java.io.BufferedWriter(new java.io.FileWriter(file))
        try writeSections(writer, newSections)
        finally {
          writer.close()
        }
      }
    }
  }
}
