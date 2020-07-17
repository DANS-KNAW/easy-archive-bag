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

import java.util.UUID

import nl.knaw.dans.easy.archivebag.EasyArchiveBag.{ getReferredBagUser, getUserLines }
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Failure

class EasyArchiveBagSpec extends AnyFlatSpec with Matchers with Inside {

  private val bagId = UUID.randomUUID()
  private val infoText = "Payload-Oxum: 3212481.4\n" +
    "Bagging-Date: 2019-05-23\nBag-Size: 3.1 MB\n" +
    "Created: 2016-11-12T23:41:11.000+00:00\n" +
    "EASY-User-Account: user001"
  private val infoTextNoUser = "Payload-Oxum: 3212481.4\n" +
    "Bagging-Date: 2019-05-23\nBag-Size: 3.1 MB\n" +
    "Created: 2016-11-12T23:41:11.000+00:00\n"

  "getReferredBagUser" should "return the correct user" in {
    getReferredBagUser(bagId, getUserLines(infoText)).get shouldBe "user001"
  }

  it should "fail when there is no user account given in the info.txt" in {
    getReferredBagUser(bagId, getUserLines(infoTextNoUser)) should matchPattern { case Failure(NoUserException(`bagId`)) => }
  }
}
