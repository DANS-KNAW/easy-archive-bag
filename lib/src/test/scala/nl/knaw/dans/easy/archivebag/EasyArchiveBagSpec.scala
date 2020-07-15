package nl.knaw.dans.easy.archivebag

import java.util.UUID

import nl.knaw.dans.easy.archivebag.EasyArchiveBag.{ getUser, getUserLines }
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

  "getUser" should "return the correct user" in {
    getUser(bagId, getUserLines(infoText)).get shouldBe "user001"
  }

  it should "fail when there is no user account given in the info.txt" in {
    getUser(bagId, getUserLines(infoTextNoUser)) should matchPattern { case Failure(NoUserException(`bagId`)) => }
  }
}
