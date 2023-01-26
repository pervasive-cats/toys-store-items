/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.valueobjects

import io.github.pervasivecats.items.Validated
import io.github.pervasivecats.items.ValidationError

import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

type AmountDouble = Double Refined Positive

trait Amount {

  val value: AmountDouble
}

object Amount {

  final private case class AmountImpl(value: AmountDouble) extends Amount

  case object WrongAmountFormat extends ValidationError {

    override val message: String = "The Amount is a negative value"
  }

  def apply(value: Double): Validated[Amount] = applyRef[AmountDouble](value) match {
    case Left(_) => Left[ValidationError, Amount](WrongAmountFormat)
    case Right(value) => Right[ValidationError, Amount](AmountImpl(value))
  }
}
