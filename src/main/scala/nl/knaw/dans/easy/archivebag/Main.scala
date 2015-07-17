package nl.knaw.dans.easy.archivebag

import java.io.{ByteArrayOutputStream, File, FileInputStream, FileOutputStream, OutputStream}
import java.math.BigInteger
import java.nio.file.Paths
import java.security.{DigestOutputStream, MessageDigest}
import java.util.zip.{ZipEntry, ZipOutputStream}

import org.apache.commons.io.IOUtils
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.{ContentType, FileEntity}
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClients}
import org.slf4j.LoggerFactory

object Main {
  val log = LoggerFactory.getLogger(getClass)
  val EASY_BAGIT_URI = "http://easy.dans.knaw.nl/schemas/EASY-BagIt/2015-07-17"
  def main(args: Array[String]) {
    log.debug("Parsing command line arguments")
    val opts = new Conf(args)
    val bagDir = opts.bagDirectory()
    val username = opts.username()
    val password = opts.password()
    val storageServiceUrl = opts.storageServiceUrl()

    log.debug("Generating unique temporary file name")
    val tempFile = File.createTempFile("easy-archive-bag", "zip")
    tempFile.delete()

    log.debug("Zipping bag dir to file at {}", tempFile)
    val fos = new FileOutputStream(tempFile)
    val md5digest = MessageDigest.getInstance("MD5")
    compressDirToOutputStream(bagDir, new DigestOutputStream(fos, md5digest))

    val md5hex = new BigInteger(1, md5digest.digest).toString(16)
    log.debug("Content-MD5 = {}", md5hex)

    log.info("Sending bag to {}, with user = {}, password = {}", storageServiceUrl, username, "*****")
    val credsProv = new BasicCredentialsProvider
    credsProv.setCredentials(new AuthScope(storageServiceUrl.getHost, storageServiceUrl.getPort), new UsernamePasswordCredentials(username, password))
    val http = HttpClients.custom.setDefaultCredentialsProvider(credsProv).build()
    val post = new HttpPost(storageServiceUrl.toURI)
    post.addHeader("Content-Disposition", "attachment; filename=bag.zip")
    post.addHeader("Content-MD5", md5hex)
    post.addHeader("Packaging", EASY_BAGIT_URI)
    post.setEntity(new FileEntity(tempFile, ContentType.create("application/zip")))
    val response = http.execute(post)
    if (log.isDebugEnabled) {
      val os = new ByteArrayOutputStream()
      IOUtils.copy(response.getEntity.getContent(), os)
      log.debug("Response entity={}", os.toString("UTF-8"))
    }
    response.getStatusLine.getStatusCode match {
      case 201 =>
        log.info("SUCCESS")
        log.info("Deposit created at: {}", response.getFirstHeader("Location").getValue)
      case _ => log.error("Deposit failed: {}", response.getStatusLine)
    }
    tempFile.deleteOnExit()
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
    recursiveListFiles(dir).map {
      case f: File =>
        val relativePath = pathBase.relativize(Paths.get(f.getAbsolutePath))
        val pathString = relativePath + 
            (if (f.isDirectory()) "/" 
             else "")
        zos.putNextEntry(new ZipEntry(pathString))
        if (f.isFile) writeFile(f)
        zos.closeEntry()
    }
    zos.close()
  }

  def recursiveListFiles(f: File): Array[File] = {
    val fs = f.listFiles
    fs ++ fs.filter(_.isDirectory).flatMap(recursiveListFiles)
  }
}