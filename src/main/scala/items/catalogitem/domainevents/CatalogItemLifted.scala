/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.domainevents

import items.catalogitem.valueobjects.{CatalogItemId, Store}

trait CatalogItemLifted {

  val catalogItemId: CatalogItemId

  val store: Store
}

object CatalogItemLifted {

  private case class CatalogItemLiftedImpl(catalogItemId: CatalogItemId, store: Store) extends CatalogItemLifted

  def apply(catalogItemId: CatalogItemId, store: Store): CatalogItemLifted = CatalogItemLiftedImpl(catalogItemId, store)
}
