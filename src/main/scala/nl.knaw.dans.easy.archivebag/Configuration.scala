/**
 * Copyright (C) 2015 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.archivebag

import java.nio.file.{ Files, Paths }

import org.apache.commons.configuration.PropertiesConfiguration
import resource.managed

import scala.io.Source

case class Configuration(version: String, properties: PropertiesConfiguration)

object Configuration {

  def apply(): Configuration = {
    val home = Paths.get(System.getProperty("app.home"))
    val defaultPath = Paths.get(s"/etc/opt/dans.knaw.nl/easy-archive-bag/")
    val configuredPath = home.resolve("cfg")
    val cfgPath = Seq(defaultPath, configuredPath)
      .find(Files.exists(_))
      .getOrElse { throw new IllegalStateException("No configuration directory found") }

    val versionFile = home.resolve("bin/version").toFile
    val configFile = cfgPath.resolve("application.properties").toFile
    new Configuration(
      version = managed(Source.fromFile(versionFile)).acquireAndGet(_.mkString),
      properties = new PropertiesConfiguration(configFile)
    )
  }
}
