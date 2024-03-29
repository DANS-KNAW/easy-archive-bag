/*
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

import java.io.{ File, IOException }
import java.net.URI
import java.nio.file.Path

import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import nl.knaw.dans.easy.validate.DansBagValidationResult
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.string._
import nl.knaw.dans.lib.encode.StringEncoding
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import resource.Using
import scalaj.http.HttpResponse

import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

object EasyArchiveBag extends Bagit5FacadeComponent with DebugEnhancedLogging {

  override val bagFacade = new Bagit5Facade()

  def run(implicit ps: Parameters): Try[URI] = {
    logger.info(s"[${ ps.bagId }] Archiving bag")
    for {
      maybeVersionOfId <- bagFacade.getIsVersionOf(ps.bagDir.toPath)
      _ <- handleIsVersionOf(maybeVersionOfId)
      zippedBag <- generateUncreatedTempFile()
      _ <- zipDir(ps.bagDir, zippedBag)
      response <- putFile(zippedBag)
      location <- handleBagStoreResponse(response)
        .doIfSuccess(_ => zippedBag.delete())
        .doIfFailure { case _ => zippedBag.delete() }
    } yield location
  }

  private def handleIsVersionOf(maybeVersionOfId: Option[BagId])(implicit ps: Parameters): Try[Unit] = {
    if (maybeVersionOfId.isDefined) {
      for {
        _ <- validateBag()
        _ <- createRefBagsTxt(maybeVersionOfId.get)
      } yield ()
    }
    else
      Success(())
  }

  private def handleBagStoreResponse(response: HttpResponse[String])(implicit ps: Parameters): Try[URI] = {
    response.code match {
      case 201 =>
        response.location
          .map(loc => Success(new URI(loc)))
          .getOrElse(Failure(new RuntimeException("No bag archival location found in response after successful upload")))
          .doIfSuccess(uri => logger.info(s"Bag archival location created at: $uri"))
          .flatMap(location => addBagToIndex(ps.bagId)
            .map(_ => location)
            .recover {
              case t =>
                logger.warn(s"BAG ${ ps.bagId } NOT ADDED TO BAG-INDEX. SUBSEQUENT REVISIONS WILL NOT BE PRUNED", t)
                location
            })
      case 401 =>
        Failure(UnauthorizedException(ps.bagId))
      case _ =>
        logger.error(s"${ ps.storageDepositService } returned:[ ${ response.statusLine } ]. Body = ${ response.body }")
        Failure(new RuntimeException(s"Bag archiving failed: ${ response.statusLine }"))
    }
  }

  private def addBagToIndex(bagId: BagId)(implicit ps: Parameters): Try[Unit] = Try {
    val response = ps.http(ps.bagIndexService.resolve(s"bags/$bagId").toASCIIString)
      .method("PUT")
      .asString

    response.code match {
      case 201 => ()
      case _ =>
        logger.error(s"${ ps.bagIndexService } returned:[ ${ response.statusLine } ] while adding new bag to bag index. Body = ${ response.body }")
        throw new IllegalStateException("Error trying to add bag to index")
    }
  }

  def validateBag()(implicit ps: Parameters): Try[Unit] = {
    Try {
      val bagDirUri = ps.bagDir.toPath.toUri.toString.escapeString
      val bagStoreUri = ps.storageDepositService.toString.escapeString
      val validationUrlString = s"${ ps.validateDansBagService }validate?infoPackageType=AIP&bag-store=$bagStoreUri&uri=$bagDirUri"
      logger.info(s"Calling Dans Bag Validation Service with ${ validationUrlString }")
      ps.http(s"${ validationUrlString }")
        .timeout(connTimeoutMs = 10000, readTimeoutMs = ps.readTimeOut)
        .method("POST")
        .header("Accept", "application/json")
        .asString
    } flatMap {
      case r if r.code == 200 => for {
        result <- DansBagValidationResult.fromJson(r.body)
        validResult <- checkDansBagValidationResult(result)
      } yield validResult
      case r => Failure(new RuntimeException(s"Dans Bag Validation failed (${ r.code }): ${ r.body }"))
    }
  }

  private def checkDansBagValidationResult(result: DansBagValidationResult)(implicit ps: Parameters): Try[Unit] = {
    if (result.isCompliant)
      Success(())
    else
      Failure(BagValidationException(ps.bagId, result.ruleViolations.get.mkString))
  }

  private def createRefBagsTxt(versionOfId: BagId)(implicit ps: Parameters): Try[Unit] = {
    for {
      refBagsTxt <- getBagSequence(versionOfId)
      _ <- writeRefBagsTxt(ps.bagDir.toPath)(refBagsTxt)
    } yield ()
  }

  private def getBagSequence(bagId: BagId)(implicit ps: Parameters): Try[String] = Try {
    val uri = ps.bagIndexService.resolve(s"bag-sequence?contains=$bagId")
    val response = ps.http(uri.toASCIIString)
      .header("Accept", "text/plain;charset=utf-8")
      .asString

    response.code match {
      case 200 =>
        val body = response.body
        if (body.isBlank) {
          logger.error(s"Empty response body from [$uri]")
          throw InvalidIsVersionOfException(s"Bag with bag-id $bagId, pointed to by Is-Version-Of field in bag-info.txt is not found in bag index.")
        }
        else body
      case _ =>
        logger.error(s"$uri returned: [ ${ response.statusLine } ] while getting bag Sequence for bag $bagId. Body = ${ response.body }")
        throw new IllegalStateException(s"Error retrieving bag-sequence for bag: $bagId. [$uri] returned ${ response.statusLine }")
    }
  }

  private def writeRefBagsTxt(bagDir: Path)(refBagsTxt: String): Try[Unit] = Try {
    FileUtils.write(bagDir.resolve("refbags.txt").toFile, refBagsTxt, "UTF-8")
  }

  private def generateUncreatedTempFile()(implicit tempDir: File): Try[File] = Try {
    val tempFile = File.createTempFile("easy-archive-bag-", ".zip", tempDir)
    tempFile.delete()
    debug(s"Generated unique temporary file name: $tempFile")
    tempFile
  } recoverWith {
    case NonFatal(e) => Failure(new IOException(s"Could not create temp file in $tempDir: ${ e.getMessage }", e))
  }

  private def zipDir(dir: File, zip: File)(implicit bagId: BagId): Try[Unit] = Try {
    logger.info(s"[$bagId] Zipping directory $dir to file $zip")
    if (zip.exists) zip.delete
    val zf = new ZipFile(zip)
    val parameters = new ZipParameters
    zf.addFolder(dir, parameters)
  }

  private def putFile(file: File)(implicit s: Parameters): Try[HttpResponse[String]] = {
    for {
      md5Hex <- computeMd5(file)
      _ = debug(s"Content-MD5 = $md5Hex")
      _ = logger.info(s"Sending bag to ${ s.storageDepositService }, id = ${ s.bagId }, with user = ${ s.username }, password = ****")
      response <- Using.fileInputStream(file)
        .map(fileStream => {
          s.http(s.storageDepositService.toURI.resolve("bags/").resolve(s.bagId.toString).toASCIIString)
            .copy(connectFunc = InputStreamBodyConnectFunc(fileStream, Option(file.length)))
            .header("Content-Disposition", "attachment; filename=bag.zip")
            .header("Content-MD5", md5Hex)
            .header("Content-Type", "application/zip")
            .method("PUT")
            .auth(s.username, s.password)
            .asString
        })
        .tried
    } yield response
  }

  private def computeMd5(file: File): Try[String] = {
    Using.fileInputStream(file).map(DigestUtils.md5Hex).tried
  }
}
