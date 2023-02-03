/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.domainevents

import items.catalogitem.valueobjects.{CatalogItemId, Store}
import items.item.valueobjects.ItemId

trait ItemReturned {

  val catalogItemId: CatalogItemId

  val store: Store

  val itemId: ItemId
}

object ItemReturned {

  private case class ItemReturnedImpl(catalogItemId: CatalogItemId, store: Store, itemId: ItemId) extends ItemReturned

  def apply(catalogItemId: CatalogItemId, store: Store, itemId: ItemId): ItemReturned =
    ItemReturnedImpl(catalogItemId, store, itemId)
}
