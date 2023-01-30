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

class LiftedCatalogItemTest extends AnyFunSpec {

  private val id: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
  private val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
  private val store: Store = Store(13).getOrElse(fail())
  private val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
  val liftedCatalogItem: LiftedCatalogItem = LiftedCatalogItem(id, category, store, price)

  describe("A lifted catalog item") {
    describe("when created with a id, a category, a store and a price") {
      it("should contain them") {
        liftedCatalogItem.id shouldBe id
        liftedCatalogItem.category shouldBe category
        liftedCatalogItem.store shouldBe store
        liftedCatalogItem.price shouldBe price
      }
    }

    describe("when updated with a new price") {
      it("should contain that") {
        val newPrice: Price = Price(Amount(14.99).getOrElse(fail()), Currency.withName("EUR"))
        liftedCatalogItem.updated(price = newPrice).price shouldBe newPrice
      }
    }

    describe("when compared with a object that got the same id and different parameters") {
      it("should be true") {
        val newCategory: ItemCategoryId = ItemCategoryId(11).getOrElse(fail())
        val newStore: Store = Store(6).getOrElse(fail())
        val newPrice: Price = Price(Amount(42.50).getOrElse(fail()), Currency.withName("USD"))
        val newCatalogItem: LiftedCatalogItem = LiftedCatalogItem(id, newCategory, newStore, newPrice)
        liftedCatalogItem === newCatalogItem shouldBe true
      }
    }

    describe("when compared with a object that got a different id and the same parameters") {
      it("should be false") {
        val newId: CatalogItemId = CatalogItemId(5437).getOrElse(fail())
        val newCatalogItem: LiftedCatalogItem = LiftedCatalogItem(newId, category, store, price)
        liftedCatalogItem === newCatalogItem shouldBe false
      }
    }

    describe("when compared with an object with the same hashcode") {
      it("should be true") {
        val newCategory: ItemCategoryId = ItemCategoryId(11).getOrElse(fail())
        val newStore: Store = Store(6).getOrElse(fail())
        val newPrice: Price = Price(Amount(42.50).getOrElse(fail()), Currency.withName("USD"))
        val newCatalogItem: LiftedCatalogItem = LiftedCatalogItem(id, newCategory, newStore, newPrice)
        liftedCatalogItem.hashCode shouldBe newCatalogItem.hashCode
      }
    }

    describe("when compared with an object with a different hashcode") {
      it("should be true") {
        val newId: CatalogItemId = CatalogItemId(14142).getOrElse(fail())
        val newCatalogItem: LiftedCatalogItem = LiftedCatalogItem(newId, category, store, price)
        liftedCatalogItem.hashCode === newCatalogItem.hashCode shouldBe false
      }
    }

    describe("when it is placed on the shelf") {
      it("should contain the same values") {
        val inPlaceCatalogItem: InPlaceCatalogItem = liftedCatalogItem.putInPlace
        inPlaceCatalogItem.id shouldBe liftedCatalogItem.id
        inPlaceCatalogItem.category shouldBe liftedCatalogItem.category
        inPlaceCatalogItem.store shouldBe liftedCatalogItem.store
        inPlaceCatalogItem.price shouldBe liftedCatalogItem.price
      }
    }
  }
}
