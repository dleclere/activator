/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package activator

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Json._

object RequestHelpers {
  import JsonHelper._

  def extractTypeOnly[T](typeName: String, value: T): Reads[T] =
    extractTagged("type", typeName)(Reads[T](_ => JsSuccess(value)))

  def extractType[T](typeName: String)(reads: Reads[T]): Reads[T] =
    extractTagged("type", typeName)(reads)
}
