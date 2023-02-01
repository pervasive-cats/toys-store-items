/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.entities

trait ReturnedItemOps[A <: ReturnedItem] {

  def putInPlace(returnedItem: ReturnedItem): InPlaceItem
}

object ReturnedItemOps {

  extension [A <: ReturnedItem: ReturnedItemOps](returnedItem: A) {

    def putInPlace: InPlaceItem = implicitly[ReturnedItemOps[A]].putInPlace(returnedItem)
  }
}
