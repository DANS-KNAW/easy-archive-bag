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

import java.io.{ InputStream, OutputStream }
import java.net.HttpURLConnection

import scalaj.http.HttpRequest

import scala.annotation.tailrec

case class InputStreamBodyConnectFunc(inputStream: InputStream) extends ((HttpRequest, HttpURLConnection) => Unit) {
  def apply(req: HttpRequest, conn: HttpURLConnection): Unit = {
    conn.setDoOutput(true)
    conn.connect()

    readOnce(new Array[Byte](req.sendBufferSize), conn.getOutputStream, 0L)
  }

  @tailrec
  private def readOnce(buffer: Array[Byte], out: OutputStream, writtenBytes: Long): Unit = {
    val len = inputStream.read(buffer)
    var bytesWritten = writtenBytes
    if (len > 0) {
      out.write(buffer, 0, len)
      bytesWritten += len
    }

    if (len >= 0)
      readOnce(buffer, out, bytesWritten)
  }

  override def toString = s"InputStreamBodyConnectFunc($inputStream)"
}
