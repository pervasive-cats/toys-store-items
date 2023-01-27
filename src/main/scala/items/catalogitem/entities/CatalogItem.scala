/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import items.catalogitem.valueobjects.{CatalogItemId, Price, Store}
import items.itemcategory.valueobjects.ItemCategoryId

import io.github.pervasivecats.AnyOps.===

trait CatalogItem {

  val id: CatalogItemId

  val category: ItemCategoryId

  val store: Store

  val price: Price
}

object CatalogItem {

  private case class CatalogItemImpl(
    id: CatalogItemId,
    category: ItemCategoryId,
    store: Store,
    price: Price
  ) extends CatalogItem {

    override def equals(obj: Any): Boolean = obj match {
      case catalogItem: CatalogItem => catalogItem.id === id
      case _ => false
    }

    override def hashCode(): Int = id.hashCode()
  }

  given CatalogItemOps[CatalogItem] with {

    override def updated(
      catalogItem: CatalogItem,
      price: Price
    ): CatalogItem = CatalogItemImpl(catalogItem.id, catalogItem.category, catalogItem.store, price)
  }

  def apply(
    itemId: CatalogItemId,
    id: ItemCategoryId,
    store: Store,
    price: Price
  ): CatalogItem = CatalogItemImpl(itemId, id, store, price)
}
