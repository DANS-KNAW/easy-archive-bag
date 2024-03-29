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

import java.io.{ InputStream, OutputStream }
import java.net.HttpURLConnection

import scalaj.http.HttpRequest

import scala.annotation.tailrec

// TODO submitted a PR to scalaj-http for adding this functionality.
//      https://github.com/scalaj/scalaj-http/pull/188
//  once this is merged, we can remove this class here
case class InputStreamBodyConnectFunc(inputStream: InputStream, streamLength: Option[Long] = None) extends ((HttpRequest, HttpURLConnection) => Unit) {
  def apply(req: HttpRequest, conn: HttpURLConnection): Unit = {
    val bufferSize = req.sendBufferSize

    streamLength match {
      case Some(value) => conn.setFixedLengthStreamingMode(value)
      case None => conn.setChunkedStreamingMode(bufferSize)
    }
    conn.setDoOutput(true)
    conn.connect()

    recursive(new Array[Byte](bufferSize), conn.getOutputStream, 0L)
  }

  @tailrec
  private def recursive(buffer: Array[Byte], out: OutputStream, writtenBytes: Long): Unit = {
    val len = inputStream.read(buffer)
    var bytesWritten = writtenBytes
    if (len > 0) {
      out.write(buffer, 0, len)
      bytesWritten += len
    }

    if (len >= 0)
      recursive(buffer, out, bytesWritten)
  }

  override def toString = s"InputStreamBodyConnectFunc($inputStream)"
}
