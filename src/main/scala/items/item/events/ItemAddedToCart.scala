/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.events

import items.catalogitem.valueobjects.{CatalogItemId, Store}
import items.item.valueobjects.{Customer, ItemId}

trait ItemAddedToCart {

  val catalogItemId: CatalogItemId

  val store: Store

  val itemId: ItemId

  val customer: Customer
}
