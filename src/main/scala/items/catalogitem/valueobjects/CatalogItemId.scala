/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.valueobjects

import eu.timepit.refined.api.RefType.applyRef

import items.{Id, Validated, ValidationError}

trait CatalogItemId {

  val value: Id
}

object CatalogItemId {

  final private case class CatalogItemIdImpl(value: Id) extends CatalogItemId

  case object WrongCatalogItemIdFormat extends ValidationError {

    override val message: String = "The catalog item id is a negative value"
  }

  def apply(value: Long): Validated[CatalogItemId] = applyRef[Id](value) match {
    case Left(_) => Left[ValidationError, CatalogItemId](WrongCatalogItemIdFormat)
    case Right(value) => Right[ValidationError, CatalogItemId](CatalogItemIdImpl(value))
  }

}
