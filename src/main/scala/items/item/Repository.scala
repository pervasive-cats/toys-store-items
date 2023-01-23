/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item

import items.catalogitem.valueobjects.{CatalogItemId, Store}
import items.item.valueobjects.ItemId
import items.Validated
import items.item.entities.{Item, ReturnedItem}

trait Repository {

  def findById(itemId: ItemId, catalogItemId: CatalogItemId, store: Store): Validated[Item]

  def findAllReturned(): Validated[Set[ReturnedItem]]

  def add(item: Item): Validated[Unit]

  def update(item: Item): Validated[Unit]

  def remove(item: Item): Validated[Unit]
}
