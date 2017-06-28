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

import java.io.IOException
import java.nio.file.Path
import java.util.Map.Entry

import gov.loc.repository.bagit.domain.Bag
import gov.loc.repository.bagit.exceptions._
import gov.loc.repository.bagit.reader.BagReader
import nl.knaw.dans.lib.logging.DebugEnhancedLogging

import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.util.{ Failure, Try }

// TODO: Code duplication from easy-bag-index. Candidate for new library easy-bagit-lib (a facade over the LOC lib)
trait BagFacadeComponent {

  val bagFacade: BagFacade

  trait BagFacade {
    def getBagInfo(bagDir: Path): Try[Map[String, String]]
  }
}

trait Bagit4FacadeComponent extends BagFacadeComponent {
  this: DebugEnhancedLogging =>

  class Bagit4Facade(bagReader: BagReader = new BagReader) extends BagFacade {

    private def entryToTuple[K, V](entry: Entry[K, V]): (K, V) = entry.getKey -> entry.getValue

    def getBagInfo(bagDir: Path): Try[Map[String, String]] = {
      trace(bagDir)
      getBag(bagDir).map(_.getMetadata.getAll.asScala.map(entryToTuple).toMap)
    }

    private def getBag(bagDir: Path): Try[Bag] = Try {
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
  }
}
