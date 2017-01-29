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

import java.nio.file.Path

import gov.loc.repository.bagit.{Bag, BagFactory}

import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.util.{Failure, Try}

// TODO: Code duplication from easy-bag-index. Candidate for new library easy-bagit-lib (a facade over the LOC lib)
trait BagFacadeComponent {

  val bagFacade: BagFacade

  trait BagFacade {
    def getBagInfo(bagDir: Path): Try[Map[String, String]]
  }
}

trait Bagit4FacadeComponent extends BagFacadeComponent {
  class Bagit4Facade(bagFactory: BagFactory = new BagFactory) extends BagFacade {

    def getBagInfo(bagDir: Path): Try[Map[String, String]] = {
      for {
        // TODO: close bag using scala-arm
        bag <- getBag(bagDir)
      } yield bag.getBagInfoTxt.asScala.toMap
    }

    private def getBag(bagDir: Path): Try[Bag] = Try {
      bagFactory.createBag(bagDir.toFile, BagFactory.Version.V0_97, BagFactory.LoadOption.BY_MANIFESTS)
    }.recoverWith { case cause => Failure(BagNotFoundException(bagDir, cause)) }
  }
}
