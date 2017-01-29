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
package nl.knaw.dans.easy.archivebag

import java.io.File
import java.net.{URI, URL}
import java.util.UUID

import org.apache.commons.configuration.PropertiesConfiguration
import org.rogach.scallop.{ScallopConf, ScallopOption}
import org.slf4j.LoggerFactory

object CommandLineOptions {

  val log = LoggerFactory.getLogger(getClass)

  def parse(args: Array[String]): Parameters = {
    log.debug("Loading application.properties ...")

    val homeDir = new File(System.getProperty("app.home"))
    val props = {
      val ps = new PropertiesConfiguration()
      ps.setDelimiterParsingDisabled(true)
      ps.load(new File(homeDir, "cfg/application.properties"))
      ps
    }
    log.debug("Parsing command line ...")
    val conf = new ScallopCommandLine(props, args)
    conf.verify()

    val settings = Parameters(
      username = conf.username(),
      password = conf.password(),
      bagDir = conf.bagDirectory(),
      storageDepositService = conf.storageServiceUrl(),
      bagIndexService = new URI(props.getString("bag-index.uri")),
      uuid = UUID.fromString(conf.uuid()))

    log.debug(s"Using the following settings: $settings")

    settings
  }
}

class ScallopCommandLine(props: PropertiesConfiguration, args: Array[String]) extends ScallopConf(args) {

  appendDefaultToDescription = true
  editBuilder(_.setHelpWidth(110))

  printedName = "easy-archive-bag"
  version(s"$printedName ${Version()}")
  banner(s"""
                |Send a bag to archival storage.
                |
                |Usage: $printedName <bag-directory> [<storage-service-url>]
                |Options:
                |""".stripMargin)

  val username: ScallopOption[String] = opt[String]("username",
    descr = "Username to use for authentication/authorisation to the storage service",
    default = props.getString("default.storage-service-username") match {
      case s: String => Some(s)
      case _ => throw new RuntimeException("No username provided")
    })

  val password: ScallopOption[String] = opt[String]("password",
    descr = "Password to use for authentication/authorisation to the storage service",
    default = props.getString("default.storage-service-password") match {
      case s: String => Some(s)
      case _ => throw new RuntimeException("No password provided")
    })

  val bagDirectory: ScallopOption[File] = trailArg[File](name = "bag-directory", required = true,
    descr = "Directory in BagIt format that will be sent to archival storage")

  val uuid: ScallopOption[String] = trailArg[String](
    name= "uuid",
    descr = "Identifier for the bag in archival storage",
    required = true)

  val storageServiceUrl: ScallopOption[URL] = trailArg[URL](
    name = "storage-service-url",
    required = false,
    default = props.getString("default.storage-service-url") match {
      case s: String => Some(new URL(s))
      case _ => throw new RuntimeException("No storage service URL provided")
    })


  validateFileExists(bagDirectory)
  validateOpt(bagDirectory) {
    case Some(dir) =>
      Right(Unit)
    case _ => Left("Could not parse parameter <bag-directory>")
  }
}
