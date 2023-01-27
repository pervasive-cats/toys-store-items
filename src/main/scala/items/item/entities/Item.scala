/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.entities

import items.catalogitem.entities.CatalogItem
import items.item.valueobjects.ItemId

trait Item {

  val id: ItemId

  val kind: CatalogItem
}
