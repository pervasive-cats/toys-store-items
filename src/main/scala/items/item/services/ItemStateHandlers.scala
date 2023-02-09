/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.services

import io.github.pervasivecats.items.item.entities.{InCartItem, InPlaceItem, ReturnedItem}
import items.item.Repository as ItemRepository
import items.catalogitem.Repository as CatalogItemRepository
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem}
import items.item.domainevents.{ItemAddedToCart, ItemPutInPlace, ItemReturned}

trait ItemStateHandlers {

  def onItemAddedToCart(event: ItemAddedToCart)(using ItemRepository, CatalogItemRepository): Unit

  def onItemReturned(event: ItemReturned)(using ItemRepository, CatalogItemRepository): Unit

  def onItemPutInPlace(event: ItemPutInPlace)(using ItemRepository,CatalogItemRepository): Unit
}

object ItemStateHandlers extends ItemStateHandlers {

  override def onItemAddedToCart(event: ItemAddedToCart)(using ItemRepository,CatalogItemRepository): Unit = {
    summon[CatalogItemRepository]
      .findById(event.catalogItemId, event.store)
      .map(catalogItem => summon[ItemRepository].update(InCartItem(event.itemId, catalogItem, event.customer)))
  }

  override def onItemReturned(event: ItemReturned)(using ItemRepository, CatalogItemRepository): Unit = {
    summon[CatalogItemRepository]
      .findById(event.catalogItemId, event.store)
      .map(catalogItem => summon[ItemRepository].update(ReturnedItem(event.itemId, catalogItem)))
  }

  override def onItemPutInPlace(event: ItemPutInPlace)(using ItemRepository, CatalogItemRepository): Unit = {
    summon[CatalogItemRepository]
      .findById(event.catalogItemId, event.store)
      .map(catalogItem => summon[ItemRepository].update(InPlaceItem(event.itemId, catalogItem)))
  }
}
