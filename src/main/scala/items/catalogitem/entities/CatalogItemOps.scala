/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import items.catalogitem.valueobjects.Price

trait CatalogItemOps[A <: CatalogItem] {

  def updated(catalogItem: A, price: Price): A

  def lift(catalogItem: A): Validated[LiftedCatalogItem]
}

object CatalogItemOps {

  extension [A <: CatalogItem: CatalogItemOps](catalogItem: A) {

    def updated(price: Price): A = implicitly[CatalogItemOps[A]].updated(catalogItem, price)

    def lift: Validated[LiftedCatalogItem] = implicitly[CatalogItemOps[A]].lift(catalogItem)
  }
}
