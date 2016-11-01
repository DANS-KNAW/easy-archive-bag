package nl.knaw.dans.easy

import java.io.File
import java.net.URL

package object archivebag {

  case class Settings(username: String,
                      password: String,
                      checkInterval: Int,
                      maxCheckCount: Int,
                      bagDir: File,
                      slug: Option[String],
                      storageDepositService: URL)

  object Version {
    def apply(): String = {
      val props = new java.util.Properties()
      props.load(Version.getClass.getResourceAsStream("/Version.properties"))
      props.getProperty("application.version")
    }
  }
}
