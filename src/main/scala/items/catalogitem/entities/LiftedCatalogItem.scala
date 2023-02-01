/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import io.github.pervasivecats.items.Validated
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId

import AnyOps.*
import items.catalogitem.valueobjects.{CatalogItemId, Price, Store}

trait LiftedCatalogItem extends CatalogItem

object LiftedCatalogItem {

  private case class LiftedCatalogItemImpl(id: CatalogItemId, category: ItemCategoryId, store: Store, price: Price)
    extends LiftedCatalogItem {

    override def equals(obj: Any): Boolean = obj match {
      case catalogItem: CatalogItem => catalogItem.id === id
      case _ => false
    }

    override def hashCode(): Int = id.##
  }

  given LiftedCatalogItemOps[LiftedCatalogItem] with {

    override def putInPlace(liftedCatalogItem: LiftedCatalogItem): InPlaceCatalogItem = InPlaceCatalogItem(
      liftedCatalogItem.id,
      liftedCatalogItem.category,
      liftedCatalogItem.store,
      liftedCatalogItem.price
    )
  }

  given CatalogItemOps[LiftedCatalogItem] with {

    override def updated(
      catalogItem: LiftedCatalogItem,
      price: Price
    ): LiftedCatalogItem = LiftedCatalogItemImpl(catalogItem.id, catalogItem.category, catalogItem.store, price)
  }

  def apply(id: CatalogItemId, category: ItemCategoryId, store: Store, price: Price): LiftedCatalogItem =
    LiftedCatalogItemImpl(id, category, store, price)
}
