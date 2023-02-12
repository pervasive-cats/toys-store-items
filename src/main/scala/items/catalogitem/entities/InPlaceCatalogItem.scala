/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import io.github.pervasivecats.Validated
import io.github.pervasivecats.ValidationError

import eu.timepit.refined.api.RefType.applyRef

import items.itemcategory.valueobjects.ItemCategoryId
import AnyOps.*
import items.catalogitem.valueobjects.{CatalogItemId, Count, Price, Store}

trait InPlaceCatalogItem extends CatalogItem

object InPlaceCatalogItem {

  private case class InPlaceCatalogItemImpl(id: CatalogItemId, category: ItemCategoryId, store: Store, price: Price)
    extends InPlaceCatalogItem {

    override def equals(obj: Any): Boolean = obj match {
      case catalogItem: CatalogItem => catalogItem.id === id
      case _ => false
    }

    override def hashCode(): Int = id.##
  }

  given CatalogItemOps[InPlaceCatalogItem] with {

    override def updated(catalogItem: InPlaceCatalogItem, price: Price): InPlaceCatalogItem =
      InPlaceCatalogItemImpl(catalogItem.id, catalogItem.category, catalogItem.store, price)

    override def lift(catalogItem: InPlaceCatalogItem): Validated[LiftedCatalogItem] =
      Count(1L).map(LiftedCatalogItem(catalogItem.id, catalogItem.category, catalogItem.store, catalogItem.price, _))
  }

  def apply(id: CatalogItemId, category: ItemCategoryId, store: Store, price: Price): InPlaceCatalogItem =
    InPlaceCatalogItemImpl(id, category, store, price)
}
