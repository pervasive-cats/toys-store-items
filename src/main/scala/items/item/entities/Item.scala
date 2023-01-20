/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.entities

import items.item.valueobjects.*
import items.catalogitem.entities.CatalogItem

trait Item {

  val id: ItemId
  val kind: CatalogItem
}
