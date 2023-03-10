/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory.valueobjects

import io.github.pervasivecats.Validated
import io.github.pervasivecats.ValidationError

import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.MatchesRegex

type NameString = String Refined MatchesRegex["^[\\w\\*\\$][\\w\\s\\-\\$]*(\\(\\d{1,}\\)){0,1}[^.\\s]$"]

trait Name {

  val value: NameString
}

object Name {

  final private case class NameImpl(value: NameString) extends Name

  case object WrongNameFormat extends ValidationError {

    override val message: String = "The name format is invalid"
  }

  def apply(value: String): Validated[Name] = applyRef[NameString](value) match {
    case Left(_) => Left[ValidationError, Name](WrongNameFormat)
    case Right(value) => Right[ValidationError, Name](NameImpl(value))
  }
}
