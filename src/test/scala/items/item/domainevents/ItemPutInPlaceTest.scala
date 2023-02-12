/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.domainevents

import io.github.pervasivecats.items.item.valueobjects.Customer
import io.github.pervasivecats.items.item.valueobjects.ItemId

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

import items.catalogitem.valueobjects.{CatalogItemId, Store}

class ItemPutInPlaceTest extends AnyFunSpec {

  val catalogItemId: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
  val store: Store = Store(39).getOrElse(fail())
  val itemId: ItemId = ItemId(9000).getOrElse(fail())
  val itemPutInPlace: ItemPutInPlace = ItemPutInPlace(catalogItemId, store, itemId)

  describe("An item put in place event") {
    describe("when created with a catalog item id, a store and an item id") {
      it("should contain them") {
        itemPutInPlace.catalogItemId shouldBe catalogItemId
        itemPutInPlace.store shouldBe store
        itemPutInPlace.itemId shouldBe itemId
      }
    }
  }
}
