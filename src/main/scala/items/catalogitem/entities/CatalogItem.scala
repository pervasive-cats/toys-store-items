/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import io.github.pervasivecats.AnyOps.===

import items.catalogitem.valueobjects.{CatalogItemId, Price, Store}
import items.itemcategory.valueobjects.ItemCategoryId

trait CatalogItem {

  val id: CatalogItemId

  val category: ItemCategoryId

  val store: Store

  val price: Price
}
