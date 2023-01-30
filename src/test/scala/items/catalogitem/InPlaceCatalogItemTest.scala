package io.github.pervasivecats
package items.catalogitem

import io.github.pervasivecats.items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import io.github.pervasivecats.items.catalogitem.entities.CatalogItemOps.updated
import io.github.pervasivecats.items.catalogitem.entities.InPlaceCatalogItem.*
import io.github.pervasivecats.items.catalogitem.entities.InPlaceCatalogItemOps.lift
import io.github.pervasivecats.items.catalogitem.entities.LiftedCatalogItemOps.putInPlace
import io.github.pervasivecats.items.catalogitem.valueobjects.Amount
import io.github.pervasivecats.items.catalogitem.valueobjects.CatalogItemId
import io.github.pervasivecats.items.catalogitem.valueobjects.Currency
import io.github.pervasivecats.items.catalogitem.valueobjects.Price
import io.github.pervasivecats.items.catalogitem.valueobjects.Store
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

class InPlaceCatalogItemTest extends AnyFunSpec {

  private val id: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
  private val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
  private val store: Store = Store(13).getOrElse(fail())
  private val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
  val inPlaceCatalogItem: InPlaceCatalogItem = InPlaceCatalogItem(id, category, store, price)

  describe("An in place catalog item") {
    describe("when created with a id, a category, a store and a price") {
      it("should contain them") {
        inPlaceCatalogItem.id shouldBe id
        inPlaceCatalogItem.category shouldBe category
        inPlaceCatalogItem.store shouldBe store
        inPlaceCatalogItem.price shouldBe price
      }
    }

    describe("when updated with a new price") {
      it("should contain that") {
        val newPrice: Price = Price(Amount(14.99).getOrElse(fail()), Currency.withName("EUR"))
        inPlaceCatalogItem.updated(price = newPrice).price shouldBe newPrice
      }
    }

    describe("when compared with a object that got the same id and different parameters") {
      it("should be true") {
        val newCategory: ItemCategoryId = ItemCategoryId(11).getOrElse(fail())
        val newStore: Store = Store(6).getOrElse(fail())
        val newPrice: Price = Price(Amount(42.50).getOrElse(fail()), Currency.withName("USD"))
        val newCatalogItem: InPlaceCatalogItem = InPlaceCatalogItem(id, newCategory, newStore, newPrice)
        inPlaceCatalogItem === newCatalogItem shouldBe true
      }
    }

    describe("when compared with a object that got a different id and the same parameters") {
      it("should be false") {
        val newId: CatalogItemId = CatalogItemId(5437).getOrElse(fail())
        val newCatalogItem: InPlaceCatalogItem = InPlaceCatalogItem(newId, category, store, price)
        inPlaceCatalogItem === newCatalogItem shouldBe false
      }
    }

    describe("when compared with an object with the same hashcode") {
      it("should be true") {
        val newCategory: ItemCategoryId = ItemCategoryId(11).getOrElse(fail())
        val newStore: Store = Store(6).getOrElse(fail())
        val newPrice: Price = Price(Amount(42.50).getOrElse(fail()), Currency.withName("USD"))
        val newCatalogItem: InPlaceCatalogItem = InPlaceCatalogItem(id, newCategory, newStore, newPrice)
        inPlaceCatalogItem.hashCode shouldBe newCatalogItem.hashCode
      }
    }

    describe("when its lifted") {
      it("should contain the same values") {
        val liftedCatalogItem: LiftedCatalogItem = inPlaceCatalogItem.lift
        liftedCatalogItem.id shouldBe inPlaceCatalogItem.id
        liftedCatalogItem.category shouldBe inPlaceCatalogItem.category
        liftedCatalogItem.store shouldBe inPlaceCatalogItem.store
        liftedCatalogItem.price shouldBe inPlaceCatalogItem.price
      }
    }
  }
}
