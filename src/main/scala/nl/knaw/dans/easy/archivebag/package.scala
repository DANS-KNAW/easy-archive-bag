/**
 * Copyright (C) 2015 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
