package io.github.pervasivecats
package items.item.domainevents

import io.github.pervasivecats.items.item.valueobjects.Customer
import io.github.pervasivecats.items.item.valueobjects.ItemId

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

import items.catalogitem.valueobjects.{CatalogItemId, Store}

class ItemAddedToCartTest extends AnyFunSpec {

  val catalogItemId: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
  val store: Store = Store(39).getOrElse(fail())
  val itemId: ItemId = ItemId(9000).getOrElse(fail())
  val customer: Customer = Customer("addr.3ss.!@email.test.com").getOrElse(fail())
  val itemAddedToCart: ItemAddedToCart = ItemAddedToCart(catalogItemId, store, itemId, customer)

  describe("An Item added to cart event") {
    describe("when created with a catalog item id, a store, an item id and a customer") {
      it("should contain them") {
        itemAddedToCart.catalogItemId shouldBe catalogItemId
        itemAddedToCart.store shouldBe store
        itemAddedToCart.itemId shouldBe itemId
        itemAddedToCart.customer shouldBe customer
      }
    }
  }
}
