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

import java.net.{ URI, URL }

import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import scalaj.http.{ Http, HttpResponse }

import scala.util.{ Failure, Success, Try }

/**
 * Simple, incomplete interface to the bag store service that provides methods:
 *  - to read the bag info.txt
 *  - to check existence of a bag in a specific bagstore
 */
trait BagStore extends DebugEnhancedLogging {
  val bagStoreBaseUri: URI
  val bagStoreUri: URI
  val connectionTimeoutMs = 1000
  val readTimeoutMs = 5000

  /**
   * Fetches bag info.txt for a given UUID.
   *
   * @param bagId the bag-id of the bag
   * @return bag info.txt
   */
  def getBagInfoText(bagId: BagId): Try[String] = {
    val url = bagStoreBaseUri.resolve(s"bags/$bagId/bag-info.txt").toASCIIString
    debug(s"calling $url")

    Try {
      Http(url)
        .timeout(connTimeoutMs = connectionTimeoutMs, readTimeoutMs = readTimeoutMs)
        .asString
    }
      .flatMap {
        case HttpResponse(body, 200, _) =>
          Success(body)
        case HttpResponse(body, code, _) =>
          logger.error(s"call to $url failed: $code - $body")
          Failure(BagStoreException(url, code))
      }
  }

  /**
   * Checks existence of a bag for a given UUID in this specific store.
   *
   * @param bagId    the bag-id of the bag
   * @return boolean
   */
  def bagExists(bagId: BagId): Try[Boolean] = {
    trace(bagId)
    val bagUrl = bagStoreUri.resolve(s"bags/$bagId").toASCIIString
    debug(s"Requesting: $bagUrl")
    Try {
      Http(bagUrl)
        .header("Accept", "text/plain")
        .timeout(connTimeoutMs = connectionTimeoutMs, readTimeoutMs = readTimeoutMs)
        .method("HEAD")
        .asBytes.code
    }
      .flatMap {
        case code if (code == 200 || code == 201) => Success(true)
        case code if (code == 404) => Success(false)
        case code: Int => Failure(new Exception(s"Reading from store $bagStoreUri failed, HTTP code: $code"))
      }
  }
}

object BagStore {
  def apply(storeUrl: URL): BagStore = new BagStore() {
    private val s = storeUrl.toString
    override val bagStoreBaseUri: URI = new URI(s.substring(0,s.indexOf("/stores") + 1))
    override val bagStoreUri: URI = storeUrl.toURI
  }
}
