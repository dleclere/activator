import sbt._
import Keys._
import com.typesafe.sbt.S3Plugin._

object NewsHelper {
  val newsVersion = SettingKey[String]("news-version")
  val newsFile = SettingKey[File]("activator-news-file")
  val jsonpNewsFile = SettingKey[File]("activator-jsonp-news-file")
  val jsonpNewsFileCreated = TaskKey[File]("activator-jsonp-news-file-created")
  val publishNews = InputKey[Unit]("publish-news")
  
  
  def generateJsonp(htmlFile: File, jsonpFile: File): File = {
    val html = IO read htmlFile

    import scala.util.parsing.json._
    val json = JSONObject(Map(
      "html" -> html    
    ))
    val jsonp = "setNewsJson("+json+");"
    
    if(!jsonpFile.getParentFile.exists) jsonpFile.getParentFile.mkdirs()
    IO.write(jsonpFile, jsonp)
    
    jsonpFile
  }
  
  import sbt.complete.DefaultParsers._
  import sbt.complete.Parser
  
  def versionParser: Parser[String] = NotSpace.* map (_ mkString "")
  def publishParser = Space ~> token(versionParser, "<version>")
  
  def publishNewsVersion(v: String, state: State): Unit = {
    val state2 = injectVersion(switchToNewsProject(state), v)
    val extracted = Project.extract(state2)
    val result = extracted.runTask(S3.upload in extracted.currentRef, state2)
  }
  
  def switchToNewsProject(state: State): State = {
    val session = Project.session(state)
    val newProj = session.setCurrent(session.currentBuild, "news", session.currentEval)
    Project.setProject(newProj, Project.structure(state), state)
  }
  
  def injectVersion(state: State, v: String): State = {
    val session = Project.session(state)    
    val extracted = Project.extract(state)
    //TODO - hack for news project!    
    val structure = Project.structure(state)
    val newSettings: Seq[Setting[_]] =
      (newsVersion in Global := v)
    implicit val display = Project.showContextKey(state)  
    val newStructure = Load.reapply(
        session.original ++ newSettings, 
        structure)
    Project.setProject(session, newStructure, state)
  }
  
  
  def settings: Seq[Setting[_]] =
    s3Settings ++
    Seq[Setting[_]](
      newsFile <<= baseDirectory apply (_ / "news.html"),
      jsonpNewsFile <<= target apply (_ / "news.jsonp"),
      jsonpNewsFileCreated <<= (newsFile, jsonpNewsFile) map generateJsonp,
      newsVersion in Global <<= version,
      mappings in S3.upload <<= (jsonpNewsFileCreated, newsVersion) map { (news, v) =>
        Seq(news -> ("typesafe-activator/%s/news.js" format (v, v)))
      },
      S3.host in S3.upload := "downloads.typesafe.com.s3.amazonaws.com",
      S3.progress in S3.upload := true,
      publishNews <<= InputTask(_ => publishParser) { v =>
        (v, state) map publishNewsVersion
          
      }
    )
}
