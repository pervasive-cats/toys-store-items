/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

trait InPlaceCatalogItemOps[A <: InPlaceCatalogItem] {

  def lift(inPlaceCatalogItem: InPlaceCatalogItem): LiftedCatalogItem
}

object InPlaceCatalogItemOps {

  extension [A <: InPlaceCatalogItem: InPlaceCatalogItemOps](inPlaceCatalogItem: A) {

    def lift: LiftedCatalogItem = implicitly[InPlaceCatalogItemOps[A]].lift(inPlaceCatalogItem)
  }
}
