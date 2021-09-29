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
package nl.knaw.dans.easy.validate

import org.json4s.ext.EnumNameSerializer
import org.json4s.native.JsonMethods.parse
import org.json4s.{ DefaultFormats, Formats }

import scala.util.{ Failure, Try }

/**
 * Result object of calling the easy-validate-dans-bag service. This is the Scala representation of
 * the JSON response object.
 *
 * @param bagUri          The URI of the bag to validate.
 * @param bag             The name of the bag.
 * @param infoPackageType `SIP` or `AIP`: the type of information package the bag was validated as.
 * @param profileVersion  The version of the profile used in the validation.
 * @param isCompliant     Signals whether the bag is compliant with the validated rules. If this
 *                        field is `true`, `ruleViolations` is expected to be empty.
 * @param ruleViolations  The rules that were violated. The violations are (rule number, violation
 *                        details) pairs. This field is absent if `isCompliant` is `true`.
 */
case class DansBagValidationResult(bagUri: String,
                                   bag: String,
                                   infoPackageType: InformationPackageType.Value,
                                   profileVersion: Int,
                                   isCompliant: Boolean,
                                   ruleViolations: Option[Seq[(String, String)]])
object DansBagValidationResult {
  private implicit val jsonFormats: Formats = DefaultFormats +
    new EnumNameSerializer(InformationPackageType)

  def fromJson(input: String): Try[DansBagValidationResult] = {
    Try(parse(input).extract[DansBagValidationResult]).recoverWith { case t =>
      Failure(new Exception(s"parse error [${ t.getClass }: ${ t.getMessage }] for: $input", t))
    }
  }
}

object InformationPackageType extends Enumeration {
  type InformationPackageType = Value
  val AIP, SIP = Value
}

