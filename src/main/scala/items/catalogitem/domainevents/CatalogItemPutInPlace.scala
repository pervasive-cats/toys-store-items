/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.domainevents

import items.catalogitem.valueobjects.{CatalogItemId, Store}

trait CatalogItemPutInPlace {

  val catalogItemId: CatalogItemId

  val store: Store
}

object CatalogItemPutInPlace {

  private case class CatalogItemPutInPlaceImpl(catalogItemId: CatalogItemId, store: Store) extends CatalogItemPutInPlace

  def apply(catalogItemId: CatalogItemId, store: Store): CatalogItemPutInPlace = CatalogItemPutInPlaceImpl(catalogItemId, store)
}
