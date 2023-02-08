/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import io.github.pervasivecats.Validated
import items.itemcategory.valueobjects.ItemCategoryId
import AnyOps.*
import items.catalogitem.valueobjects.{CatalogItemId, Count, Price, Store}
import items.catalogitem.entities.LiftedCatalogItemOps.updated
import eu.timepit.refined.auto.given

trait LiftedCatalogItem extends CatalogItem {

  val count: Count
}

object LiftedCatalogItem {

  private case class LiftedCatalogItemImpl(id: CatalogItemId, category: ItemCategoryId, store: Store, price: Price, count: Count)
    extends LiftedCatalogItem {

    override def equals(obj: Any): Boolean = obj match {
      case catalogItem: CatalogItem => catalogItem.id === id
      case _ => false
    }

    override def hashCode(): Int = id.##
  }

  given LiftedCatalogItemOps[LiftedCatalogItem] with {

    override def updated(
      catalogItem: LiftedCatalogItem,
      count: Count,
      price: Price
    ): LiftedCatalogItem = LiftedCatalogItemImpl(catalogItem.id, catalogItem.category, catalogItem.store, price, count)

    override def putInPlace(liftedCatalogItem: LiftedCatalogItem): Validated[CatalogItem] =
      (liftedCatalogItem.count.value: Long) match {
        case 1L =>
          Right[ValidationError, CatalogItem](
            InPlaceCatalogItem(liftedCatalogItem.id, liftedCatalogItem.category, liftedCatalogItem.store, liftedCatalogItem.price)
          )
        case _ => Count(liftedCatalogItem.count.value - 1).map(c => liftedCatalogItem.updated(count = c))
      }
  }

  def apply(id: CatalogItemId, category: ItemCategoryId, store: Store, price: Price, count: Count): LiftedCatalogItem =
    LiftedCatalogItemImpl(id, category, store, price, count)
}
