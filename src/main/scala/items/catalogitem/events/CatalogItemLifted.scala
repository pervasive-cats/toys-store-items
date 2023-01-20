/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.events

import items.catalogitem.valueobjects.{CatalogItemId, Store}

trait CatalogItemLifted {

  val catalogItemId: CatalogItemId
  val store: Store
}
