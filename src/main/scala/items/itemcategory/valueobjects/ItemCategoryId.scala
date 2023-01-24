/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory.valueobjects

import io.github.pervasivecats.items.Id
import io.github.pervasivecats.items.Validated
import io.github.pervasivecats.items.ValidationError

import eu.timepit.refined.api.RefType.applyRef
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

trait ItemCategoryId {

  val value: Id
}

object ItemCategoryId {

  final private case class ItemCategoryIdImpl(value: Id) extends ItemCategoryId

  case object WrongStoreIdFormat extends ValidationError {

    override val message: String = "The item category id is a negative value"
  }

  def apply(value: Long): Validated[ItemCategoryId] = applyRef[Id](value) match {
    case Left(_) => Left[ValidationError, ItemCategoryId](WrongStoreIdFormat)
    case Right(value) => Right[ValidationError, ItemCategoryId](ItemCategoryIdImpl(value))
  }
}
