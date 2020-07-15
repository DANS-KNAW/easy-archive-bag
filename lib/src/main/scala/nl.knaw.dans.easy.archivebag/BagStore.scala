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
  val bagStoreBaseUrl: URI
  val connectionTimeoutMs: Int
  val readTimeoutMs: Int

  /**
   * Fetches bag info.txt for a given UUID.
   *
   * @param bagId the bag-id of the bag
   * @return bag info.txt
   */
  def getBagInfoText(bagId: BagId): Try[String] = {
    val url = bagStoreBaseUrl.resolve(s"bags/$bagId/bag-info.txt").toASCIIString
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
   * Checks existence of bag info.txt for a given UUID.
   *
   * @param storeUrl url to the store where to look for the bag
   * @param bagId    the bag-id of the bag
   * @return boolean
   */
  def bagExists(storeUrl: URL, bagId: BagId): Try[Boolean] = {
    trace(bagId)
    val bagUrl = storeUrl.toURI.resolve(s"$bagId").toASCIIString
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
        case code: Int => Failure(new Exception(s"Reading from store $storeUrl failed, HTTP code: $code"))
      }
  }
}

object BagStore {
  def apply(baseUrl: URI, cto: Int, rto: Int): BagStore = new BagStore() {
    override val bagStoreBaseUrl: URI = baseUrl
    override val connectionTimeoutMs: Int = cto
    override val readTimeoutMs: Int = rto
  }
}
