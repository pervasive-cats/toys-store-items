/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.entities

import items.item.valueobjects.Customer

trait InPlaceItemOps[A <: InPlaceItem] {

  def putInCart(inPlaceItem: InPlaceItem, customer: Customer): InCartItem
}

object InPlaceItemOps {

  extension [A <: InPlaceItem: InPlaceItemOps](inPlaceItem: A) {

    def putInCart(customer: Customer): InCartItem = implicitly[InPlaceItemOps[A]].putInCart(inPlaceItem, customer)
  }

}
