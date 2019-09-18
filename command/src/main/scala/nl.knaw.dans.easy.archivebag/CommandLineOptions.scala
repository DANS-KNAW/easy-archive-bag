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

import java.io.File
import java.net.{ MalformedURLException, URI, URL }
import java.nio.file.Paths
import java.util.UUID

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.rogach.scallop.{ ScallopConf, ScallopOption, ValueConverter, singleArgConverter }

object CommandLineOptions extends DebugEnhancedLogging {
  def parse(args: Array[String]): Parameters = {
    debug("Loading application.properties ...")

    val configuration = Configuration(Paths.get(System.getProperty("app.home")))
    debug("Parsing command line ...")
    val cmd = new ScallopCommandLine(configuration, args)
    cmd.verify()

    val settings = Parameters(
      username = cmd.username(),
      password = cmd.password(),
      bagDir = cmd.bagDirectory(),
      tempDir = new File(configuration.properties.getString("tempdir")),
      storageDepositService = cmd.bagStoreUrl(),
      bagIndexService = new URI(configuration.properties.getString("bag-index.uri")),
      bagId = UUID.fromString(cmd.uuid()),
      userAgent = s"easy-archive-bag/${configuration.version}",
    )

    debug(s"Using the following settings: $settings")

    settings
  }
}

class ScallopCommandLine(configuration: Configuration, args: Array[String]) extends ScallopConf(args) {

  private implicit val urlConverter: ValueConverter[URL] = singleArgConverter(s => new URL(addTrailingSlashIfNeeded(s)), {
    case e: MalformedURLException => Left(s"bad URL, ${ e.getMessage }")
  })

  private def addTrailingSlashIfNeeded(s: String): String = {
    if (s endsWith "/") s
    else s"$s/"
  }

  appendDefaultToDescription = true
  editBuilder(_.setHelpWidth(110))

  printedName = "easy-archive-bag"
  version(s"$printedName ${ configuration.version }")
  banner(
    s"""
       |Send a bag to archival storage.
       |
       |Usage: $printedName <bag-directory> <uuid> [<storage-service-url>]
       |Options:
       |""".stripMargin)

  val username: ScallopOption[String] = opt[String](
    name = "username",
    short = 'u',
    descr = "Username to use for authentication/authorisation to the storage service",
    required = true,
  )

  val password: ScallopOption[String] = opt[String](
    name = "password",
    short = 'p',
    descr = "Password to use for authentication/authorisation to the storage service",
    required = true,
  )

  val bagDirectory: ScallopOption[File] = trailArg[File](
    name = "bag-directory",
    descr = "Directory in BagIt format that will be sent to archival storage",
  )

  val uuid: ScallopOption[String] = trailArg[String](
    name = "uuid",
    descr = "Identifier for the bag in archival storage",
  )

  val bagStoreUrl: ScallopOption[URL] = trailArg[URL](
    name = "bag-store-url",
    descr = "base url to the store in which the bag needs to be archived",
  )

  validateFileExists(bagDirectory)
  validateOpt(bagDirectory) {
    case Some(_) => Right(Unit)
    case _ => Left("Could not parse parameter <bag-directory>")
  }
}
