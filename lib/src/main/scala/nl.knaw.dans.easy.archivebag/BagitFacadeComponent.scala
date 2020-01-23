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

import java.io.IOException
import java.net.URI
import java.nio.file.Path
import java.util.Map.Entry
import java.util.UUID

import gov.loc.repository.bagit.domain.Bag
import gov.loc.repository.bagit.exceptions._
import gov.loc.repository.bagit.reader.BagReader
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.string._

import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

trait BagFacadeComponent {

  val bagFacade: BagFacade

  val IS_VERSION_OF_KEY = "Is-Version-Of"

  trait BagFacade {
    def getIsVersionOf(bagDir: Path): Try[Option[BagId]]
  }
}

trait Bagit5FacadeComponent extends BagFacadeComponent {
  this: DebugEnhancedLogging =>

  class Bagit5Facade(bagReader: BagReader = new BagReader) extends BagFacade {

    private def entryToTuple[K, V](entry: Entry[K, V]): (K, V) = entry.getKey -> entry.getValue

    def getIsVersionOf(bagDir: Path): Try[Option[BagId]] = {
      for {
        info <- getBagInfo(bagDir)
        versionOf <- info.get(IS_VERSION_OF_KEY)
          .map(s => Try { new URI(s) }.flatMap(getIsVersionOfFromUri).map(Option(_)))
          .getOrElse(Success(None))
      } yield versionOf
    }

    def getBagInfo(bagDir: Path): Try[Map[String, String]] = {
      trace(bagDir)
      getBag(bagDir).map(_.getMetadata.getAll.asScala.map(entryToTuple).toMap)
    }

    def getBag(bagDir: Path): Try[Bag] = Try {
      bagReader.read(bagDir)
    }.recoverWith {
      case cause: IOException => Failure(NotABagDirException(bagDir, cause))
      case cause: UnparsableVersionException => Failure(BagReaderException(bagDir, cause))
      case cause: MaliciousPathException => Failure(BagReaderException(bagDir, cause))
      case cause: InvalidBagMetadataException => Failure(BagReaderException(bagDir, cause))
      case cause: UnsupportedAlgorithmException => Failure(BagReaderException(bagDir, cause))
      case cause: InvalidBagitFileFormatException => Failure(BagReaderException(bagDir, cause))
      case NonFatal(cause) => Failure(NotABagDirException(bagDir, cause))
    }

    // TODO: candidate for easy-bagit-lib
    private def getIsVersionOfFromUri(uri: URI): Try[UUID] = {
      if (uri.getScheme == "urn") {
        val uuidPart = uri.getSchemeSpecificPart
        val parts = uuidPart.split(':')
        if (parts.length != 2) Failure(InvalidIsVersionOfException(uri.toASCIIString))
        else parts(1).toUUID.toTry
      }
      else Failure(InvalidIsVersionOfException(uri.toASCIIString))
    }
  }
}
