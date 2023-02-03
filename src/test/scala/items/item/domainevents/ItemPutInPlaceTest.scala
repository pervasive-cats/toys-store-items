package io.github.pervasivecats
package items.item.domainevents

import io.github.pervasivecats.items.item.valueobjects.{Customer, ItemId}
import items.catalogitem.valueobjects.{CatalogItemId, Store}

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

class ItemPutInPlaceTest extends AnyFunSpec {

  val catalogItemId: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
  val store: Store = Store(39).getOrElse(fail())
  val itemId: ItemId = ItemId(9000).getOrElse(fail())
  val itemAddedToCart: ItemPutInPlace = ItemPutInPlace(catalogItemId, store, itemId)

  describe("An item put in place") {
    describe("when created with a catalog item id, a store and an item id") {
      it("should contain them") {
        itemAddedToCart.catalogItemId shouldBe catalogItemId
        itemAddedToCart.store shouldBe store
        itemAddedToCart.itemId shouldBe itemId
      }
    }
  }
}
