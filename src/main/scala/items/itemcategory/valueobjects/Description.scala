/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory.valueobjects

import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.MatchesRegex

import items.Validated
import io.github.pervasivecats.ValidationError

type DescriptionString = String Refined MatchesRegex["^[a-zA-Z0-9\\W]{1,300}$"]

trait Description {

  val value: DescriptionString
}

object Description {

  final private case class DescriptionImpl(value: DescriptionString) extends Description

  case object WrongDescriptionFormat extends ValidationError {

    override val message: String = "The description format is invalid"
  }

  def apply(value: String): Validated[Description] = applyRef[DescriptionString](value) match {
    case Left(_) => Left[ValidationError, Description](WrongDescriptionFormat)
    case Right(value) => Right[ValidationError, Description](DescriptionImpl(value))
  }
}
