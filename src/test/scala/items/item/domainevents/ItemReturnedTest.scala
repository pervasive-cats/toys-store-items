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

class ItemReturnedTest extends AnyFunSpec {

  val catalogItemId: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
  val store: Store = Store(39).getOrElse(fail())
  val itemId: ItemId = ItemId(9000).getOrElse(fail())
  val itemReturned: ItemReturned = ItemReturned(catalogItemId, store, itemId)

  describe("An returned item event") {
    describe("when created with a catalog item id, a store and an item id") {
      it("should contain them") {
        itemReturned.catalogItemId shouldBe catalogItemId
        itemReturned.store shouldBe store
        itemReturned.itemId shouldBe itemId
      }
    }
  }
}
