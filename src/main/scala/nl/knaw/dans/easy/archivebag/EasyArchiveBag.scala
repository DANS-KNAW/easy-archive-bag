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
import java.net.{URI, URL}
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipOutputStream}

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.net.URLCodec
import org.apache.commons.io.IOUtils
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpPost, HttpPut}
import org.apache.http.entity.{ContentType, FileEntity}
import org.apache.http.impl.client.{BasicCredentialsProvider, CloseableHttpClient, HttpClients}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}
import scala.xml._

object EasyArchiveBag {
  val log = LoggerFactory.getLogger(getClass)
  type BagId = String

  def main(args: Array[String]) {
    implicit val settings = CommandLineOptions.parse(args)
    run.get
  }

  def run(implicit ps: Parameters): Try[URI] = Try {
    for {
      optVersionOfId <- getIsVersionOf(ps.bagDir.toPath)
      optRefBagsTxt <- optVersionOfId.map(getBagSequence).getOrElse(Success(None))
      _ <- optRefBagsTxt.map(writeRefBagsTxt(ps.bagDir.toPath))
    } yield ()

    val zippedBag = generateUncreatedTempFile()
    zipDir(ps.bagDir, zippedBag)
    val response = putFile(zippedBag)
    response.getStatusLine.getStatusCode match {
      case 201 =>
        val location = new URI(response.getFirstHeader("Location").getValue)
        log.info(s"Bag archival location created at: $location")

        // TODO: add bag to index

        location
      case _ =>
        throw new RuntimeException(s"Bag archiving failed: ${response.getStatusLine}")
    }
  }

  private def getIsVersionOf(bagDir: Path): Try[Option[BagId]] = {


    ???
  }


  private def getBagSequence(bagId: BagId)(implicit ps: Parameters): Try[Option[String]] = {


    ???
  }

  private def writeRefBagsTxt(bagDir: Path)(refBagsTxt: String): Try[Unit] = {


    ???
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
    log.info(s"Sending bag to ${s.storageDepositService}, id = ${s.uuid}, with user = ${s.username}, password = ******")
    val credsProv = new BasicCredentialsProvider
    credsProv.setCredentials(new AuthScope(s.storageDepositService.getHost, s.storageDepositService.getPort), new UsernamePasswordCredentials(s.username, s.password))
    val http = HttpClients.custom.setDefaultCredentialsProvider(credsProv).build()
    val put = new HttpPut(new URI(s.storageDepositService + s.uuid.toString))
    put.addHeader("Content-Disposition", "attachment; filename=bag.zip")
    put.addHeader("Content-MD5", md5hex)
    put.setEntity(new FileEntity(file, ContentType.create("application/zip")))
    http.execute(put)
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
