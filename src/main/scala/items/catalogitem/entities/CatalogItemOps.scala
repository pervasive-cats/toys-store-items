/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import items.catalogitem.valueobjects.Price

trait CatalogItemOps[A <: CatalogItem] {

  def updated(price: Price): A
}
