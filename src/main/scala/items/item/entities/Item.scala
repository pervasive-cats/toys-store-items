/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.entities

import io.github.pervasivecats.ValidationError

import AnyOps.===
import items.catalogitem.entities.CatalogItem
import items.item.valueobjects.ItemId

trait Item {

  val id: ItemId

  val kind: CatalogItem
}

object Item {

  case object WrongItemFormat extends ValidationError {

    override val message: String = "The item id format is invalid"
  }

  def itemEquals(obj: Any)(id: ItemId): Boolean = obj match {
    case item: Item => item.id === id
    case _ => false
  }
}
