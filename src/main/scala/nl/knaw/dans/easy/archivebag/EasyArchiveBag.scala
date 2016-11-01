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
import java.net.URL
import java.nio.file.{Files, Paths}
import java.util.zip.{ZipEntry, ZipOutputStream}

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.codec.net.URLCodec
import org.apache.commons.io.IOUtils
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpPost}
import org.apache.http.entity.{ContentType, FileEntity}
import org.apache.http.impl.client.{CloseableHttpClient, BasicCredentialsProvider, HttpClients}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Try}
import scala.xml._

object EasyArchiveBag {
  val log = LoggerFactory.getLogger(getClass)
  val BAGIT_URI = "http://purl.org/net/sword/package/BagIt"
  val STATE_FINALIZING = "FINALIZING"

  def main(args: Array[String]) {
    implicit val settings = CommandLineOptions.parse(args)
    run.get
  }

  def run(implicit s: Settings): Try[(String, String)] = Try {
    val zippedBag = generateUncreatedTempFile()
    zipDir(s.bagDir, zippedBag)
    val response = postFile(zippedBag)
    val entity = response.getEntity
    if(entity == null) throw new IllegalStateException("POST response did not contain entity")
    val bos = new ByteArrayOutputStream()
    IOUtils.copy(entity.getContent, bos)
    val entityBytes = bos.toByteArray
    bos.close()
    deleteOrWarn(zippedBag)
    response.getStatusLine.getStatusCode match {
      case 201 =>
        val location = response.getFirstHeader("Location").getValue
        log.info(s"Bag archival location created at: $location")
        getStatementUrl(new ByteArrayInputStream(entityBytes)) match {
          case Some(url) => (location, getConfirmationState(url))
          case _ =>
            throw new RuntimeException("Could not retreive Stat-IRI, so unable to validate transfer to archival storage")
        }
      case _ =>
        throw new RuntimeException(s"Bag archiving failed: ${response.getStatusLine}, ${new String(entityBytes, "UTF-8")}")
    }
  }

  def generateUncreatedTempFile(): File =  {
    val tempFile = File.createTempFile("easy-archive-bag-", ".zip")
    tempFile.delete()
    log.debug(s"Generated unique temporary file name: $tempFile")
    tempFile
  }

  def zipDir(dir: File, zip: File) = {
    log.debug(s"Zipping directory $dir to file $zip")
    val fos = new FileOutputStream(zip)
    compressDirToOutputStream(dir, fos)
  }

  def postFile(file: File)(implicit s: Settings): CloseableHttpResponse = {
    val md5hex = computeMd5(file).get
    log.debug("Content-MD5 = {}", md5hex)
    log.info("Sending bag to {}, with user = {}, password = {}", s.storageDepositService, s.username, "*****")
    val credsProv = new BasicCredentialsProvider
    credsProv.setCredentials(new AuthScope(s.storageDepositService.getHost, s.storageDepositService.getPort), new UsernamePasswordCredentials(s.username, s.password))
    val http = HttpClients.custom.setDefaultCredentialsProvider(credsProv).build()
    val post = new HttpPost(s.storageDepositService.toURI)
    post.addHeader("Content-Disposition", "attachment; filename=bag.zip")
    post.addHeader("Content-MD5", md5hex)
    post.addHeader("Packaging", BAGIT_URI)
    s.slug.map(slug => post.addHeader("Slug", new URLCodec("UTF-8").encode(slug, "UTF-8")))
    post.setEntity(new FileEntity(file, ContentType.create("application/zip")))
    http.execute(post)
  }

  def deleteOrWarn(file: File) = {
    val deleted = file.delete()
    if(!deleted) log.warn(s"Delete of $file failed")
  }

  def getStatementUrl(content: InputStream): Option[URL] = {
    val depositReceiptDoc = XML.load(content)
    val statIriLink = (depositReceiptDoc \ "link").toList.find(n => (n \@ "rel") == "http://purl.org/net/sword/terms/statement")
    val url = statIriLink.map(link => link \@ "href").map(urlString => new URL(urlString))
    content.close()
    url
  }

  def getConfirmationState(stateIri : URL)(implicit s: Settings) : String = {
    var state = STATE_FINALIZING
    val credsProv = new BasicCredentialsProvider
    credsProv.setCredentials(new AuthScope(s.storageDepositService.getHost, s.storageDepositService.getPort), new UsernamePasswordCredentials(s.username, s.password))
    val http = HttpClients.custom.setDefaultCredentialsProvider(credsProv).build()
    log.info("Waiting for final state of archived bag")
    var counter = 0;
    while (state == STATE_FINALIZING && counter < s.maxCheckCount) {
      val content = getResponseContent(stateIri, http).get
      val resp = XML.loadString(content)
      state = ((resp \"category").filter(_.attribute("label").exists(_.text.equals("State")))
        .filter(_.attribute("scheme").exists(_.text.equals("http://purl.org/net/sword/terms/state"))) \\ "@term").text
      log.info(s"Current state: ${state}")
      if (state == STATE_FINALIZING) {
        log.debug(s"Wait ${s.checkInterval} for the next check.")
        Thread.sleep(s.checkInterval);
        counter += 1
      }
    }
    state
  }

  def getResponseContent(url: URL, httpClients: CloseableHttpClient): Try[String] = {
    val httpResponse = httpClients.execute(new HttpGet(url.toURI))
    val entity = httpResponse.getEntity
    if(entity == null) Failure(new RuntimeException("Could not retrieve content from service request"))
    else {
      val is = entity.getContent
      try {
        httpResponse.getStatusLine.getStatusCode match {
          case 200 =>
            Try(io.Source.fromInputStream(is).mkString)
          case _ =>
            val content = io.Source.fromInputStream(is).mkString
            throw new RuntimeException(s"Check state failed: $content")
        }
      }
      finally {
        if (is != null) is.close()
      }
    }
  }

  def computeMd5(file: File): Try[String] = Try {
    val is = Files.newInputStream(Paths.get(file.getPath))
    try {
      DigestUtils.md5Hex(is)
    } finally {
      is.close()
    }
  }

  def compressDirToOutputStream(dir: File, os: OutputStream) {
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

  def recursiveListFiles(f: File): Array[File] = {
    val fs = f.listFiles
    fs ++ fs.filter(_.isDirectory).flatMap(recursiveListFiles)
  }
}
