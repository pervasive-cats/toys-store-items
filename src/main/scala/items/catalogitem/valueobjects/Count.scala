/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.valueobjects

import io.github.pervasivecats.Validated
import io.github.pervasivecats.ValidationError

import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

type CountLong = Long Refined Positive

trait Count {

  val value: CountLong
}

object Count {

  final private case class CountImpl(value: CountLong) extends Count

  case object WrongCountFormat extends ValidationError {

    override val message: String = "The count is a non positive value"
  }

  def apply(value: Long): Validated[Count] = applyRef[CountLong](value) match {
    case Left(_) => Left[ValidationError, Count](WrongCountFormat)
    case Right(value) => Right[ValidationError, Count](CountImpl(value))
  }
}
