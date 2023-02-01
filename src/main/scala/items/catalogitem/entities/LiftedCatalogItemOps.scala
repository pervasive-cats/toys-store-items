/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

trait LiftedCatalogItemOps[A <: LiftedCatalogItem] {

  def putInPlace(liftedCatalogItem: LiftedCatalogItem): InPlaceCatalogItem
}

object LiftedCatalogItemOps {

  extension [A <: LiftedCatalogItem: LiftedCatalogItemOps](liftedCatalogItem: A) {

    def putInPlace: InPlaceCatalogItem = implicitly[LiftedCatalogItemOps[A]].putInPlace(liftedCatalogItem)
  }
}
