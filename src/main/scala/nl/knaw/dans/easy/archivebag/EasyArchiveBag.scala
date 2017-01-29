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

import java.io._
import java.net.URI
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipOutputStream}

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.{FileUtils, IOUtils}
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpPut}
import org.apache.http.entity.{ContentType, FileEntity}
import org.apache.http.impl.client.{BasicCredentialsProvider, CloseableHttpClient, HttpClients}
import org.slf4j.LoggerFactory

import scala.util.{Success, Try}

object EasyArchiveBag extends Bagit4FacadeComponent {
  val log = LoggerFactory.getLogger(getClass)
  type BagId = String
  val bagFacade = new Bagit4Facade()

  def main(args: Array[String]) {
    implicit val settings = CommandLineOptions.parse(args)
    run.get
  }

  def run(implicit ps: Parameters): Try[URI] = Try {
    for {
      optVersionOfId <- getIsVersionOf(ps.bagDir.toPath)
      optRefBagsTxt <- optVersionOfId.map(getBagSequence).getOrElse(Success(None))
      _ <- optRefBagsTxt.map(writeRefBagsTxt(ps.bagDir.toPath)).getOrElse(Success(()))
    } yield ()

    val zippedBag = generateUncreatedTempFile()
    zipDir(ps.bagDir, zippedBag)
    val response = putFile(zippedBag)
    response.getStatusLine.getStatusCode match {
      case 201 =>
        val location = new URI(response.getFirstHeader("Location").getValue)
        log.info(s"Bag archival location created at: $location")
        addBagToIndex(ps.uuid.toString).recover {
          case t => log.warn(s"BAG ${ps.uuid} NOT ADDED TO BAG-INDEX. SUBSEQUENT REVISIONS WILL NOT BE PRUNED", t)
        }
        location
      case _ =>
        throw new RuntimeException(s"Bag archiving failed: ${response.getStatusLine}")
    }
  }

  private def addBagToIndex(bagId: BagId)(implicit ps: Parameters): Try[Unit] = Try {
    val http = createHttpClient(ps.bagIndexService.getHost, ps.bagIndexService.getPort, "", "")
    val put = new HttpPut(ps.bagIndexService.resolve(s"bags/$bagId").toASCIIString)
    log.info(s"Adding new bag to bag index with request: ${put.toString}")
    val response = http.execute(put)
    if(response.getStatusLine.getStatusCode != 201) throw new IllegalStateException("Error trying to add bag to index")
  }

  private def getIsVersionOf(bagDir: Path): Try[Option[BagId]] = {
    for {
      info <- bagFacade.getBagInfo(bagDir)
      versionOf <- Try { info.get(IS_VERSION_OF_KEY) }
    } yield versionOf
  }


  private def getBagSequence(bagId: BagId)(implicit ps: Parameters): Try[Option[String]] = {
    Try {
      val http = createHttpClient(ps.bagIndexService.getHost, ps.bagIndexService.getPort, "", "")
      val get = new HttpGet(ps.bagIndexService.resolve(s"bag-sequence?contains=$bagId"))
      get.addHeader("Accept", "text/plain;charset=utf-8")
      val sw = new StringWriter()
      val response = http.execute(get)
      if(response.getStatusLine.getStatusCode != 200) throw new IllegalStateException(s"Error retrieving bag-sequence for bag: $bagId")
      IOUtils.copy(response.getEntity.getContent, sw, "UTF-8")
      Some(sw.toString)
    }
  }

  private def writeRefBagsTxt(bagDir: Path)(refBagsTxt: String): Try[Unit] = Try {
    FileUtils.write(bagDir.toFile, refBagsTxt, "UTF-8")
  }

  private def generateUncreatedTempFile(): File =  {
    val tempFile = File.createTempFile("easy-archive-bag-", ".zip")
    tempFile.delete()
    log.debug(s"Generated unique temporary file name: $tempFile")
    tempFile
  }

  private def zipDir(dir: File, zip: File) = {
    log.debug(s"Zipping directory $dir to file $zip")
    val fos = new FileOutputStream(zip)
    compressDirToOutputStream(dir, fos)
  }

  private def putFile(file: File)(implicit s: Parameters): CloseableHttpResponse = {
    val md5hex = computeMd5(file).get
    log.debug(s"Content-MD5 = $md5hex")
    log.info(s"Sending bag to ${s.storageDepositService}, id = ${s.uuid}, with user = ${s.username}, password = ****")
    val http = createHttpClient(s.storageDepositService.getHost, s.storageDepositService.getPort, s.username, s.password)
    val put = new HttpPut(new URI(s.storageDepositService + s.uuid.toString))
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
    } finally {
      is.close()
    }
  }

  private def compressDirToOutputStream(dir: File, os: OutputStream) {
    val pathBase = Paths.get(dir.getParentFile.getAbsolutePath)
    val zos = new ZipOutputStream(os)
    def writeFile(f: File) = {
      val fis = new FileInputStream(f)
      IOUtils.copy(fis, zos)
      fis.close()
    }
    zos.putNextEntry(new ZipEntry(dir.getName + "/"))
    recursiveListFiles(dir).foreach(f => {
        val relativePath = pathBase.relativize(Paths.get(f.getAbsolutePath))
        val pathString = relativePath + 
            (if (f.isDirectory) "/"
             else "")
        zos.putNextEntry(new ZipEntry(pathString))
        if (f.isFile) writeFile(f)
        zos.closeEntry()
      }
    )
    zos.close()
  }

  private def recursiveListFiles(f: File): Array[File] = {
    val fs = f.listFiles
    fs ++ fs.filter(_.isDirectory).flatMap(recursiveListFiles)
  }
}
