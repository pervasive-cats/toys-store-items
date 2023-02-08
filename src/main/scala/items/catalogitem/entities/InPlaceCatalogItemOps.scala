/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import items.catalogitem.valueobjects.Price

trait InPlaceCatalogItemOps[A <: InPlaceCatalogItem] {

  def updated(inPlaceCatalogItem: A, price: Price): A

  def lift(inPlaceCatalogItem: A): Validated[LiftedCatalogItem]
}

object InPlaceCatalogItemOps {

  extension [A <: InPlaceCatalogItem: InPlaceCatalogItemOps](inPlaceCatalogItem: A) {

    def updated(price: Price): A = implicitly[InPlaceCatalogItemOps[A]].updated(inPlaceCatalogItem, price)
    
    def lift: Validated[LiftedCatalogItem] = implicitly[InPlaceCatalogItemOps[A]].lift(inPlaceCatalogItem)
  }
}
