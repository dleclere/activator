/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package launcher

import activator.tests._

class MustStartUI extends IntegrationTest {

  val sbtProject = makeDummySbtProject(new java.io.File("dummy"))

  val process = run_activator(Seq("ui"), sbtProject).run

  // Wait for Http Server startup on port 8888
  // TODO - If we pick a random port in the future, this needs to detect it...
  try assert(waitForHttpServerStartup("http://localhost:8888/"))
  finally {
    process.destroy()
  }
}
