/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.entities

import io.github.pervasivecats.Validated

trait InCartItemOps[A <: InCartItem] {

  def returnToStore(inCartItem: InCartItem): ReturnedItem
}

object InCartItemOps {

  extension [A <: InCartItem: InCartItemOps](inCartItem: A) {

    def returnToStore: ReturnedItem = implicitly[InCartItemOps[A]].returnToStore(inCartItem)
  }
}
