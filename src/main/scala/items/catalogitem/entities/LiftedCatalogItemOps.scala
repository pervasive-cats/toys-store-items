/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import items.catalogitem.valueobjects.{Count, Price}

trait LiftedCatalogItemOps[A <: LiftedCatalogItem] {

  def updated(liftedCatalogItem: A, count: Count, price: Price): A

  def putInPlace(liftedCatalogItem: A): Validated[CatalogItem]
}

object LiftedCatalogItemOps {

  extension [A <: LiftedCatalogItem: LiftedCatalogItemOps](liftedCatalogItem: A) {

    @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments", "scalafix:DisableSyntax.defaultArgs"))
    def updated(count: Count = liftedCatalogItem.count, price: Price = liftedCatalogItem.price): A =
      implicitly[LiftedCatalogItemOps[A]].updated(liftedCatalogItem, count, price)

    def putInPlace: Validated[CatalogItem] = implicitly[LiftedCatalogItemOps[A]].putInPlace(liftedCatalogItem)
  }
}
