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

import eu.timepit.refined.api.RefType.applyRef

import items.catalogitem.valueobjects.{CatalogItemId, Price, Store}

trait InPlaceCatalogItem extends CatalogItem

object InPlaceCatalogItem {

  private case class InPlaceCatalogItemImpl(id: CatalogItemId, category: ItemCategoryId, store: Store, price: Price)
    extends InPlaceCatalogItem

  given InPlaceCatalogItemOps[InPlaceCatalogItem] with {

    override def lift(inPlaceCatalogItem: InPlaceCatalogItem): LiftedCatalogItem = LiftedCatalogItem(
      inPlaceCatalogItem.id,
      inPlaceCatalogItem.category,
      inPlaceCatalogItem.store,
      inPlaceCatalogItem.price
    )
  }

  def apply(itemId: CatalogItemId, id: ItemCategoryId, store: Store, price: Price): InPlaceCatalogItem =
    InPlaceCatalogItemImpl(itemId, id, store, price)
}
