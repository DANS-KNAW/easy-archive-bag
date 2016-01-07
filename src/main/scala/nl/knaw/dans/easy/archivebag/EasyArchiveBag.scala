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
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.commons.io.IOUtils
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ContentType, FileEntity}
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClients}
import org.slf4j.LoggerFactory

import scala.util.Try

object Settings {
  def apply(conf: Conf): Settings =
    new Settings(
      username = conf.username(),
      password = conf.password(),
      bagDir = conf.bagDirectory(),
      storageDepositService =  conf.storageServiceUrl())
}

case class Settings(
       username: String,
       password: String,
       bagDir: File,
       storageDepositService: URL)

object EasyArchiveBag {
  val props = new PropertiesConfiguration(new File(System.getProperty("app.home"), "cfg/application.properties"))
  val log = LoggerFactory.getLogger(getClass)
  val BAGIT_URI = "http://purl.org/net/sword/package/BagIt"

  def main(args: Array[String]) {
    log.debug("Parsing command line arguments")
    val conf = new Conf(args, props)
    implicit val s = Settings(conf)
    run.get
  }

  def run(implicit s: Settings): Try[String] = Try {
    log.debug("Generating unique temporary file name")
    val tempFile = File.createTempFile("easy-archive-bag-", ".zip")
    tempFile.delete()
    log.debug("Zipping bag dir to file at {}", tempFile)
    val fos = new FileOutputStream(tempFile)
    compressDirToOutputStream(s.bagDir, fos)
    val md5hex = computeMd5(tempFile).get
    log.debug("Content-MD5 = {}", md5hex)
    log.info("Sending bag to {}, with user = {}, password = {}", s.storageDepositService, s.username, "*****")
    val credsProv = new BasicCredentialsProvider
    credsProv.setCredentials(new AuthScope(s.storageDepositService.getHost, s.storageDepositService.getPort), new UsernamePasswordCredentials(s.username, s.password))
    val http = HttpClients.custom.setDefaultCredentialsProvider(credsProv).build()
    val post = new HttpPost(s.storageDepositService.toURI)
    post.addHeader("Content-Disposition", "attachment; filename=bag.zip")
    post.addHeader("Content-MD5", md5hex)
    post.addHeader("Packaging", BAGIT_URI)
    post.setEntity(new FileEntity(tempFile, ContentType.create("application/zip")))
    val response = http.execute(post)
    if (log.isDebugEnabled) {
      val os = new ByteArrayOutputStream()
      IOUtils.copy(response.getEntity.getContent, os)
      log.debug("Response entity={}", os.toString("UTF-8"))
    }
    val deleted = tempFile.delete()
    if(!deleted) log.warn(s"Delete of $tempFile failed")
    response.getStatusLine.getStatusCode match {
      case 201 =>
        val location = response.getFirstHeader("Location").getValue
        log.info("SUCCESS")
        log.info(s"Deposit created at: $location")
        s"${location.split('/').last}/${s.bagDir.getName}"
      case _ =>
        val writer = new StringWriter()
        IOUtils.copy(response.getEntity.getContent, writer, "UTF-8")
        log.error(s"Deposit failed: ${response.getStatusLine}, ${writer.toString}")
        throw new RuntimeException("Deposit failed")
    }


    // TODO: Wait for confirmation that deposit is valid (SUBMITTED)
  }

  def computeMd5(zipFile: File): Try[String] = Try {
    val is = Files.newInputStream(Paths.get(zipFile.getPath))
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
