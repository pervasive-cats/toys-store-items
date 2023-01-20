/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.services

import items.catalogitem.events.{CatalogItemLifted, CatalogItemPutInPlace}

trait CatalogItemStateHandlers {

  def onCatalogItemPutInPlace(event: CatalogItemPutInPlace): Unit

  def onCatalogItemLifted(event: CatalogItemLifted): Unit
}
