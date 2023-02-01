/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.entities

import java.util.Objects

import items.catalogitem.entities.CatalogItem
import items.item.entities.Item.itemEquals
import items.item.valueobjects.ItemId

trait ReturnedItem extends Item

object ReturnedItem {

  private case class ReturnedItemImpl(id: ItemId, kind: CatalogItem) extends ReturnedItem {

    override def equals(obj: Any): Boolean = itemEquals(obj)(id)

    override def hashCode(): Int = Objects.hash(id)
  }

  given ReturnedItemOps[ReturnedItem] with {

    override def putInPlace(returnedItem: ReturnedItem): InPlaceItem = InPlaceItem(returnedItem.id, returnedItem.kind)
  }

  def apply(id: ItemId, kind: CatalogItem): ReturnedItem = ReturnedItemImpl(id, kind)
}
