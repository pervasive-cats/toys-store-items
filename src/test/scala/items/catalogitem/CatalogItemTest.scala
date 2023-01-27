package io.github.pervasivecats
package items.catalogitem

import io.github.pervasivecats.items.catalogitem.entities.CatalogItem
import io.github.pervasivecats.items.catalogitem.valueobjects.{Amount, CatalogItemId, Currency, Price, Store}
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId
import org.scalatest.funspec.AnyFunSpec
import io.github.pervasivecats.items.catalogitem.entities.CatalogItemOps.updated
import org.scalatest.matchers.should.Matchers.shouldBe

class CatalogItemTest extends AnyFunSpec{

  private val id: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
  private val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
  private val store: Store = Store(13).getOrElse(fail())
  private val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
  val catalogItem: CatalogItem = CatalogItem(id, category, store, price)

  describe("A catalog item") {
    describe("when created with a id, a category, a store and a price") {
      it("should contain them") {
        catalogItem.id shouldBe id
        catalogItem.category shouldBe category
        catalogItem.store shouldBe store
        catalogItem.price shouldBe price
      }
    }

    describe("when updated with a new price") {
      it("should contain that") {
        val newPrice: Price = Price(Amount(14.99).getOrElse(fail()), Currency.withName("EUR"))
        catalogItem.updated(price = newPrice).price shouldBe newPrice
      }
    }

    describe("when compared with a object that got the same id and different parameters") {
      it("should be true") {
        val newCategory: ItemCategoryId = ItemCategoryId(11).getOrElse(fail())
        val newStore: Store = Store(6).getOrElse(fail())
        val newPrice: Price = Price(Amount(42.50).getOrElse(fail()), Currency.withName("USD"))
        val newCatalogItem: CatalogItem = CatalogItem(id, newCategory, newStore, newPrice)
        catalogItem === newCatalogItem shouldBe true
      }
    }

    describe("when compared with a object that got a different id and the same parameters") {
      it("should be false") {
        val newId: CatalogItemId = CatalogItemId(5437).getOrElse(fail())
        val newCatalogItem: CatalogItem = CatalogItem(newId, category, store, price)
        catalogItem === newCatalogItem shouldBe false
      }
    }

    describe("when compared with an object with the same hashcode") {
      it("should be true") {
        val newCategory: ItemCategoryId = ItemCategoryId(11).getOrElse(fail())
        val newStore: Store = Store(6).getOrElse(fail())
        val newPrice: Price = Price(Amount(42.50).getOrElse(fail()), Currency.withName("USD"))
        val newCatalogItem: CatalogItem = CatalogItem(id, newCategory, newStore, newPrice)
        catalogItem.hashCode shouldBe newCatalogItem.hashCode
      }
    }
  }
}
