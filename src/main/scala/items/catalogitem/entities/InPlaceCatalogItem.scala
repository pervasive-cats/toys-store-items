/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import io.github.pervasivecats.items.Validated
import io.github.pervasivecats.items.ValidationError
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId
import AnyOps.*
import eu.timepit.refined.api.RefType.applyRef

import items.catalogitem.valueobjects.{CatalogItemId, Price, Store}

trait InPlaceCatalogItem extends CatalogItem

object InPlaceCatalogItem {

  private case class InPlaceCatalogItemImpl(id: CatalogItemId, category: ItemCategoryId, store: Store, price: Price)
    extends InPlaceCatalogItem {

    override def equals(obj: Any): Boolean = obj match {
      case catalogItem: CatalogItem => catalogItem.id === id
      case _ => false
    }

    override def hashCode(): Int = id.hashCode()
  }

  given InPlaceCatalogItemOps[InPlaceCatalogItem] with {

    override def lift(inPlaceCatalogItem: InPlaceCatalogItem): LiftedCatalogItem = LiftedCatalogItem(
      inPlaceCatalogItem.id,
      inPlaceCatalogItem.category,
      inPlaceCatalogItem.store,
      inPlaceCatalogItem.price
    )
  }

  given CatalogItemOps[InPlaceCatalogItem] with {

    override def updated(
                          catalogItem: InPlaceCatalogItem,
                          price: Price
                        ): InPlaceCatalogItem = InPlaceCatalogItemImpl(catalogItem.id, catalogItem.category, catalogItem.store, price)
  }

  def apply(itemId: CatalogItemId, id: ItemCategoryId, store: Store, price: Price): InPlaceCatalogItem =
    InPlaceCatalogItemImpl(itemId, id, store, price)
}
