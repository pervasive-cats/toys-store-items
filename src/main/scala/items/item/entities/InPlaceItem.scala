/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.entities

import java.util.Objects

import io.github.pervasivecats.items.ValidationError
import io.github.pervasivecats.items.item.entities.Item.WrongItemFormat
import io.github.pervasivecats.items.item.entities.Item.itemEquals
import io.github.pervasivecats.items.item.valueobjects.ItemId.WrongItemIdFormat

import eu.timepit.refined.api.RefType.applyRef

import items.catalogitem.entities.CatalogItem
import items.item.valueobjects.{Customer, ItemId}

trait InPlaceItem extends Item

object InPlaceItem {

  private case class InPlaceItemImpl(id: ItemId, kind: CatalogItem) extends InPlaceItem {

    override def equals(obj: Any): Boolean = itemEquals(obj)(id)

    override def hashCode(): Int = Objects.hash(id)
  }

  given InPlaceItemOps[InPlaceItem] with {

    override def putInCart(inPlaceItem: InPlaceItem, customer: Customer): InCartItem =
      InCartItem(customer, inPlaceItem.id, inPlaceItem.kind)
  }

  def apply(id: ItemId, kind: CatalogItem): InPlaceItem = InPlaceItemImpl(id, kind)
}
