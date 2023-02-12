/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import items.catalogitem.valueobjects.{Count, Price}

trait LiftedCatalogItemOps[A <: LiftedCatalogItem] {

  def putInPlace(liftedCatalogItem: A): Validated[CatalogItem]
}

object LiftedCatalogItemOps {

  extension [A <: LiftedCatalogItem: LiftedCatalogItemOps](liftedCatalogItem: A) {

    def putInPlace: Validated[CatalogItem] = implicitly[LiftedCatalogItemOps[A]].putInPlace(liftedCatalogItem)
  }
}
