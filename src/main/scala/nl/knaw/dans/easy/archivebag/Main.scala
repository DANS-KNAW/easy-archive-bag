package nl.knaw.dans.easy.archivebag

import org.rogach.scallop.ScallopConf
import org.slf4j.LoggerFactory
import java.net.URL
import java.io.File
import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.util.Zip4jConstants
import java.net.HttpURLConnection
import org.apache.commons.io.FileUtils
import org.apache.commons.codec.binary.Base64
import java.io.FileInputStream
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import java.io.StringWriter
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.HttpClients
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.FileEntity
import org.apache.http.entity.ContentType
import java.io.PrintWriter
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.io.BufferedOutputStream

object Main {
  val log = LoggerFactory.getLogger(getClass)
  def main(args: Array[String]) {
    log.debug("Parsing command line arguments")
    val opts = new Conf(args) 
    val bagDir = opts.bagDirectory()
    val username = opts.username()
    val password = opts.password()
    val storageServiceUrl = opts.storageServiceUrl()

    log.info("Creating zipped bag from {}", bagDir)
    val tempFile = File.createTempFile("easy-archive-bag", ".zip")
    tempFile.delete() // ... so that zip4j can create it again, yes, it's a hack ...
    log.debug("Temporary file for zipped bag: {}", tempFile)
    val p = Runtime.getRuntime.exec(s"zip -r $tempFile ${bagDir.getName}", null, bagDir.getParentFile)
    if(p.waitFor() != 0) {
      log.error("Could not create zipped bag. Zip returned: {}", p.exitValue)
    }
    val is = new FileInputStream(tempFile)
    val md5 = DigestUtils.md5Hex(is)
    is.close()
    
    log.info("Sending bag to deposit service ...")
    val credsProv = new BasicCredentialsProvider
    credsProv.setCredentials(new AuthScope(storageServiceUrl.getHost, storageServiceUrl.getPort), new UsernamePasswordCredentials("USER", "PASSWORD"))
    val http = HttpClients.custom.setDefaultCredentialsProvider(credsProv).build()
    val post = new HttpPost(storageServiceUrl.toURI)
    post.addHeader("Content-Disposition", "attachment; filename=bag.zip")
    post.addHeader("Content-MD5", md5)
    post.addHeader("Packaging", "http://easy.dans.knaw.nl/schemas/index.xml")
    post.setEntity(new FileEntity(tempFile, ContentType.create("application/zip")))
    val response = http.execute(post)
    if(log.isDebugEnabled) {
      val os = new ByteArrayOutputStream()
      IOUtils.copy(response.getEntity.getContent(), os)
      log.debug("Response entity={}", os.toString("UTF-8"))
    }
    response.getStatusLine.getStatusCode match {
      case 201 => log.info("SUCCESS")
        log.info("Deposit created at: {}", response.getFirstHeader("Location").getValue)
      case _ => log.error("Deposit failed: {}", response.getStatusLine)
    }
    tempFile.deleteOnExit()
  }
}