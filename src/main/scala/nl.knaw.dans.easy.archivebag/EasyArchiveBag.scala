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
import java.nio.file.{ Files, Path, Paths }

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.ZipParameters
import nl.knaw.dans.lib.error.TryExtensions
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.string._
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.{ FileUtils, IOUtils }
import org.apache.http.{ HttpStatus, StatusLine }
import org.apache.http.auth.{ AuthScope, UsernamePasswordCredentials }
import org.apache.http.client.methods.{ CloseableHttpResponse, HttpGet, HttpPut }
import org.apache.http.entity.{ ContentType, FileEntity }
import org.apache.http.impl.client.{ BasicCredentialsProvider, CloseableHttpClient, HttpClients }

import scala.util.control.NonFatal
import scala.util.{ Success, Try }

object EasyArchiveBag extends Bagit5FacadeComponent with DebugEnhancedLogging {

  val bagFacade = new Bagit5Facade()

  def main(args: Array[String]) {
    implicit val settings: Parameters = CommandLineOptions.parse(args)
    run.unsafeGetOrThrow
  }

  def run(implicit ps: Parameters): Try[URI] = Try {
    // TODO: refactor this function not to rely on throwing of exceptions
    (for {
      optVersionOfId <- bagFacade.getIsVersionOf(ps.bagDir.toPath)
      _ <- optVersionOfId.map(createRefBagsTxt).getOrElse(Success(()))
    } yield ()).get // trigger exception if result is Failure

    val zippedBag = generateUncreatedTempFile()
    zipDir(ps.bagDir, zippedBag)
    val response = putFile(zippedBag)
    response.getStatusLine.getStatusCode match {
      case HttpStatus.SC_CREATED =>
        val location = new URI(response.getFirstHeader("Location").getValue)
        logger.info(s"Bag archival location created at: $location")
        addBagToIndex(ps.bagId).recover {
          case t => logger.warn(s"BAG ${ ps.bagId } NOT ADDED TO BAG-INDEX. SUBSEQUENT REVISIONS WILL NOT BE PRUNED", t)
        }
        zippedBag.delete()
        location
      case HttpStatus.SC_UNAUTHORIZED =>
        throw UnautherizedException(ps.bagId)
      case _ =>
        logger.error(s"${ ps.storageDepositService } returned:[ ${ response.getStatusLine } ]. ZippedBag=$zippedBag")
        throw new RuntimeException(s"Bag archiving failed: ${ response.getStatusLine }")
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
      case HttpStatus.SC_BAD_GATEWAY =>
        logErrorAndCreateFailedHttpRequestException(statusLine)
      case _ =>
        logErrorAndCreateFailedHttpRequestException(statusLine)
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
        logger.error(s"Error retrieving bag-sequence for bag: $bagId. [${ get.getURI }] returned ${ HttpStatus.SC_BAD_REQUEST } ${ statusLine.getReasonPhrase }")
        throw new IllegalStateException(s"Error retrieving bag-sequence for bag: $bagId")
    }
    IOUtils.copy(response.getEntity.getContent, sw, "UTF-8")
    sw.toString match {
      case s if s.isBlank =>
        logger.error(s"Empty response body from [${ get.getURI }]")
        throw InvalidIsVersionOfException(s"Bag with bag-id $bagId, pointed to by Is-Version-Of field in bag-info.txt is not found in bag index.")
      case s => s
    }
  }

  private def logErrorAndCreateFailedHttpRequestException(statusLine: StatusLine) = {
    logger.error(s"Bad request while adding new bag to bag index  with message = ${ statusLine.getReasonPhrase }")
    throw new IllegalStateException("Error trying to add bag to index")
  }

  private def writeRefBagsTxt(bagDir: Path)(refBagsTxt: String): Try[Unit] = Try {
    FileUtils.write(bagDir.resolve("refbags.txt").toFile, refBagsTxt, "UTF-8")
  }

  @throws[IOException]("when creating the temp file name failed")
  private def generateUncreatedTempFile()(implicit ps: Parameters): File = try {
    val tempFile = File.createTempFile("easy-archive-bag-", ".zip", ps.tempDir)
    tempFile.delete()
    debug(s"Generated unique temporary file name: $tempFile")
    tempFile
  }
  catch {
    case NonFatal(e) => throw new IOException(s"Could not create temp file in ${ ps.tempDir }: ${ e.getMessage }", e)
  }

  private def zipDir(dir: File, zip: File): Unit = {
    debug(s"Zipping directory $dir to file $zip")
    if (zip.exists) zip.delete
    val zf = new ZipFile(zip) {
      setFileNameCharset(StandardCharsets.UTF_8.name)
    }
    val parameters = new ZipParameters
    zf.addFolder(dir, parameters)
  }

  private def putFile(file: File)(implicit s: Parameters): CloseableHttpResponse = {
    val md5hex = computeMd5(file).get
    debug(s"Content-MD5 = $md5hex")
    logger.info(s"Sending bag to ${ s.storageDepositService }, id = ${ s.bagId }, with user = ${ s.username }, password = ****")
    val http = createHttpClient(s.storageDepositService.getHost, s.storageDepositService.getPort, s.username, s.password)
    val put = new HttpPut(s.storageDepositService.toURI.resolve("bags/").resolve(s.bagId.toString))
    put.addHeader("Content-Disposition", "attachment; filename=bag.zip")
    put.addHeader("Content-MD5", md5hex)
    put.setEntity(new FileEntity(file, ContentType.create("application/zip")))
    http.execute(put)
  }

  def createHttpClient(host: String, port: Int, user: String, password: String): CloseableHttpClient = {
    val credsProv = new BasicCredentialsProvider
    credsProv.setCredentials(new AuthScope(host, port), new UsernamePasswordCredentials(user, password))
    HttpClients.custom.setDefaultCredentialsProvider(credsProv).build()
  }

  private def computeMd5(file: File): Try[String] = Try {
    val is = Files.newInputStream(Paths.get(file.getPath))
    try {
      DigestUtils.md5Hex(is)
    }
    finally {
      is.close()
    }
  }
}
