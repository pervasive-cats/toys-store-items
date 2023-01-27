/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import io.github.pervasivecats.items.itemcategory.entities.ItemCategoryOps

import items.catalogitem.valueobjects.Price

trait CatalogItemOps[A <: CatalogItem] {

  def updated(catalogItem: CatalogItem, price: Price): A
}

object CatalogItemOps {

  extension [A <: CatalogItem: CatalogItemOps](catalogItem: A) {

    @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments", "scalafix:DisableSyntax.defaultArgs"))
    def updated(price: Price = catalogItem.price): A = implicitly[CatalogItemOps[A]].updated(catalogItem, price)
  }
}
