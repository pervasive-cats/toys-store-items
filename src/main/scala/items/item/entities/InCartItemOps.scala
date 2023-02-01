/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.entities

trait InCartItemOps[A <: InCartItem] {

  def returnToStore(inCartItem: InCartItem): InPlaceItem
}

object InCartItemOps {

  extension [A <: InCartItem: InCartItemOps](inCartItem: A) {

    def returnToStore: InPlaceItem = implicitly[InCartItemOps[A]].returnToStore(inCartItem)
  }
}
