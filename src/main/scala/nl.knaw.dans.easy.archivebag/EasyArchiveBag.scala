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

import java.io._
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.ZipParameters
import nl.knaw.dans.lib.error.TryExtensions
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.string._
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.{ FileUtils, IOUtils }
import org.apache.http.HttpStatus
import org.apache.http.auth.{ AuthScope, UsernamePasswordCredentials }
import org.apache.http.client.methods.{ CloseableHttpResponse, HttpGet, HttpPut }
import org.apache.http.entity.{ ContentType, FileEntity }
import org.apache.http.impl.client.{ BasicCredentialsProvider, CloseableHttpClient, HttpClients }
import resource.{ ManagedResource, Using, managed }

import scala.io.Source
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

object EasyArchiveBag extends Bagit5FacadeComponent with DebugEnhancedLogging {

  val bagFacade = new Bagit5Facade()

  def main(args: Array[String]) {
    implicit val settings: Parameters = CommandLineOptions.parse(args)
    run.unsafeGetOrThrow
  }

  def run(implicit ps: Parameters): Try[URI] = {
    for {
      maybeVersionOfId <- bagFacade.getIsVersionOf(ps.bagDir.toPath)
      _ <- maybeVersionOfId.map(createRefBagsTxt).getOrElse(Success(()))
      zippedBag <- generateUncreatedTempFile()
      _ <- zipDir(ps.bagDir, zippedBag)
      response <- putFile(zippedBag)
      location <- response.map(handleStorageResponse).tried.flatten
        .doIfSuccess(_ => zippedBag.delete())
        .doIfFailure { case _ => zippedBag.delete() }
    } yield location
  }

  private def handleStorageResponse(response: CloseableHttpResponse)(implicit ps: Parameters) = {
    response.getStatusLine.getStatusCode match {
      case HttpStatus.SC_CREATED =>
        Try { new URI(response.getFirstHeader("Location").getValue) }
          .doIfSuccess(uri => logger.info(s"Bag archival location created at: $uri"))
          .flatMap(location => addBagToIndex(ps.bagId)
            .map(_ => location)
            .recover {
              case t =>
                logger.warn(s"BAG ${ ps.bagId } NOT ADDED TO BAG-INDEX. SUBSEQUENT REVISIONS WILL NOT BE PRUNED", t)
                location
            })
      case HttpStatus.SC_UNAUTHORIZED =>
        Failure(UnautherizedException(ps.bagId))
      case _ =>
        logger.error(s"${ ps.storageDepositService } returned:[ ${ response.getStatusLine } ]. Body = ${ getResponseBody(response) }")
        Failure(new RuntimeException(s"Bag archiving failed: ${ response.getStatusLine }"))
    }
  }

  private def addBagToIndex(bagId: BagId)(implicit ps: Parameters): Try[Unit] = Try {
    val http = createHttpClient(ps.bagIndexService.getHost, ps.bagIndexService.getPort, "", "")
    val put = new HttpPut(ps.bagIndexService.resolve(s"bags/$bagId").toASCIIString)
    logger.info(s"Adding new bag to bag index with request: ${ put.toString }")
    val response = http.execute(put)
    val statusLine = response.getStatusLine
    statusLine.getStatusCode match {
      case HttpStatus.SC_CREATED => // do nothing
      case _ =>
        logger.error(s"${ ps.storageDepositService } returned:[ ${ response.getStatusLine } ] while adding new bag to bag index. Body = ${ getResponseBody(response) }")
        throw new IllegalStateException("Error trying to add bag to index")
    }
  }

  private def createRefBagsTxt(versionOfId: BagId)(implicit ps: Parameters): Try[Unit] = {
    for {
      refBagsTxt <- getBagSequence(versionOfId)
      _ <- writeRefBagsTxt(ps.bagDir.toPath)(refBagsTxt)
    } yield ()
  }

  private def getBagSequence(bagId: BagId)(implicit ps: Parameters): Try[String] = Try {
    val http = createHttpClient(ps.bagIndexService.getHost, ps.bagIndexService.getPort, "", "")
    val get = new HttpGet(ps.bagIndexService.resolve(s"bag-sequence?contains=$bagId"))
    get.addHeader("Accept", "text/plain;charset=utf-8")
    val sw = new StringWriter()
    val response = http.execute(get)
    val statusLine = response.getStatusLine
    statusLine.getStatusCode match {
      case HttpStatus.SC_OK => // do nothing
      case _ =>
        logger.error(s"${ get.getURI } returned: [ $statusLine ] while getting bag Sequence for bag $bagId. Body = ${ getResponseBody(response) }")
        throw new IllegalStateException(s"Error retrieving bag-sequence for bag: $bagId. [${ get.getURI }] returned ${ statusLine.getStatusCode } ${ statusLine.getReasonPhrase }")
    }
    IOUtils.copy(response.getEntity.getContent, sw, "UTF-8")
    sw.toString match {
      case s if s.isBlank =>
        logger.error(s"Empty response body from [${ get.getURI }]")
        throw InvalidIsVersionOfException(s"Bag with bag-id $bagId, pointed to by Is-Version-Of field in bag-info.txt is not found in bag index.")
      case s => s
    }
  }

  private def writeRefBagsTxt(bagDir: Path)(refBagsTxt: String): Try[Unit] = Try {
    FileUtils.write(bagDir.resolve("refbags.txt").toFile, refBagsTxt, "UTF-8")
  }

  private def generateUncreatedTempFile()(implicit ps: Parameters): Try[File] = Try {
    val tempFile = File.createTempFile("easy-archive-bag-", ".zip", ps.tempDir)
    tempFile.delete()
    debug(s"Generated unique temporary file name: $tempFile")
    tempFile
  } recoverWith {
    case NonFatal(e) => Failure(new IOException(s"Could not create temp file in ${ ps.tempDir }: ${ e.getMessage }", e))
  }

  private def zipDir(dir: File, zip: File): Try[Unit] = Try {
    debug(s"Zipping directory $dir to file $zip")
    if (zip.exists) zip.delete
    val zf = new ZipFile(zip) {
      setFileNameCharset(StandardCharsets.UTF_8.name)
    }
    val parameters = new ZipParameters
    zf.addFolder(dir, parameters)
  }

  private def putFile(file: File)(implicit s: Parameters): Try[ManagedResource[CloseableHttpResponse]] = {
    for {
      md5Hex <- computeMd5(file)
      _ = debug(s"Content-MD5 = $md5Hex")
      _ = logger.info(s"Sending bag to ${ s.storageDepositService }, id = ${ s.bagId }, with user = ${ s.username }, password = ****")
      http = createHttpClient(s.storageDepositService.getHost, s.storageDepositService.getPort, s.username, s.password)
      put = new HttpPut(s.storageDepositService.toURI.resolve("bags/").resolve(s.bagId.toString)) {
        addHeader("Content-Disposition", "attachment; filename=bag.zip")
        addHeader("Content-MD5", md5Hex)
        setEntity(new FileEntity(file, ContentType.create("application/zip")))
      }
    } yield managed(http execute put)
  }

  def createHttpClient(host: String, port: Int, user: String, password: String): CloseableHttpClient = {
    val credsProv = new BasicCredentialsProvider
    credsProv.setCredentials(new AuthScope(host, port), new UsernamePasswordCredentials(user, password))
    HttpClients.custom.setDefaultCredentialsProvider(credsProv).build()
  }

  private def getResponseBody(response: CloseableHttpResponse): String = {
    Try(managed(Source.fromInputStream(response.getEntity.getContent)).acquireAndGet(_.mkString))
      .getOrRecover(t => s"responseBody not available: ${ t.getMessage }")
  }

  private def computeMd5(file: File): Try[String] = {
    Using.fileInputStream(file).map(DigestUtils.md5Hex).tried
  }
}
