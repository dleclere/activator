/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package activator

import play.api._

object Global extends GlobalSettings {

  override def beforeStart(app: Application) {
    super.beforeStart(app)
  }

  override def onStop(app: Application) {
    super.onStop(app)
    Logger.info("onStop received closing down the app")
    activator.AppManager.onApplicationStop()
  }
}
