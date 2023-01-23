/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.entities

import items.Validated

trait InPlaceItemOps[A <: InPlaceItem] {

  def putInCart(): Validated[InCartItem]
}
