/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem

import items.catalogitem.valueobjects.{CatalogItemId, Price, Store}
import items.Validated
import items.catalogitem.entities.{CatalogItem, LiftedCatalogItem}

trait Repository {

  def findById(catalogItemId: CatalogItemId, store: Store): Validated[CatalogItem]

  def findAllLifted(): Validated[Set[LiftedCatalogItem]]

  def add(catalogItem: CatalogItem): Validated[Unit]

  def update(catalogItem: CatalogItem, price: Price): Validated[Unit]

  def remove(catalogItem: CatalogItem): Validated[Unit]
}
