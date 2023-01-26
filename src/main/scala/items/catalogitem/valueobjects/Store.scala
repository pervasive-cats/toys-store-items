/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.valueobjects

import eu.timepit.refined.api.RefType.applyRef

import items.{Id, Validated, ValidationError}

trait Store {

  val id: Id
}

object Store {

  final private case class StoreImpl(id: Id) extends Store

  case object WrongStoreFormat extends ValidationError {

    override val message: String = "The Store id is a negative value"
  }

  def apply(value: Long): Validated[Store] = applyRef[Id](value) match {
    case Left(_) => Left[ValidationError, Store](WrongStoreFormat)
    case Right(value) => Right[ValidationError, Store](StoreImpl(value))
  }
}
