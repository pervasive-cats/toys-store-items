/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.services

import items.catalogitem.Repository as CatalogItemRepository
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem}
import items.item.Repository as ItemRepository
import items.item.domainevents.{ItemAddedToCart, ItemPutInPlace, ItemReturned}
import items.item.entities.{InCartItem, InPlaceItem, ReturnedItem}
import items.Validated

trait ItemStateHandlers {

  def onItemAddedToCart(event: ItemAddedToCart)(using ItemRepository, CatalogItemRepository): Validated[Unit]

  def onItemReturned(event: ItemReturned)(using ItemRepository, CatalogItemRepository): Validated[Unit]

  def onItemPutInPlace(event: ItemPutInPlace)(using ItemRepository, CatalogItemRepository): Validated[Unit]
}

object ItemStateHandlers extends ItemStateHandlers {

  override def onItemAddedToCart(event: ItemAddedToCart)(using ItemRepository, CatalogItemRepository): Validated[Unit] =
    summon[CatalogItemRepository]
      .findById(event.catalogItemId, event.store)
      .flatMap(catalogItem => summon[ItemRepository].update(InCartItem(event.itemId, catalogItem, event.customer)))

  override def onItemReturned(event: ItemReturned)(using ItemRepository, CatalogItemRepository): Validated[Unit] =
    summon[CatalogItemRepository]
      .findById(event.catalogItemId, event.store)
      .flatMap(catalogItem => summon[ItemRepository].update(ReturnedItem(event.itemId, catalogItem)))

  override def onItemPutInPlace(event: ItemPutInPlace)(using ItemRepository, CatalogItemRepository): Validated[Unit] =
    summon[CatalogItemRepository]
      .findById(event.catalogItemId, event.store)
      .flatMap(catalogItem => summon[ItemRepository].update(InPlaceItem(event.itemId, catalogItem)))
}
