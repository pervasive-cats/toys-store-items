package io.github.pervasivecats
package items.item.entities

import scala.language.postfixOps

import io.github.pervasivecats.items.catalogitem.entities.CatalogItem
import io.github.pervasivecats.items.item.entities.InCartItemOps.returnToStore
import io.github.pervasivecats.items.item.valueobjects.ItemId

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

import items.catalogitem.entities.InPlaceCatalogItem.*
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.item.entities.InPlaceItemOps.putInCart
import items.item.entities.ReturnedItemOps.putInPlace
import items.item.valueobjects.Customer
import items.itemcategory.valueobjects.ItemCategoryId

class ReturnedItemTest extends AnyFunSpec {

  private val itemId: ItemId = ItemId(9000).getOrElse(fail())

  private val catalogItemId: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
  private val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
  private val store: Store = Store(13).getOrElse(fail())
  private val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
  private val kind: LiftedCatalogItem = LiftedCatalogItem(catalogItemId, category, store, price)

  private val returnedItem: ReturnedItem = ReturnedItem(itemId, kind)

  describe("A returned item") {
    describe("when created with an id and a kind") {
      it("should contain them") {
        returnedItem.id shouldBe itemId
        returnedItem.kind shouldBe kind
      }
    }

    describe("when it is put in place") {
      it("should contain the same value") {
        val inPlaceItem: InPlaceItem = returnedItem.putInPlace
        inPlaceItem.id shouldBe returnedItem.id
        inPlaceItem.kind shouldBe returnedItem.kind
      }
    }

    val secondReturnedItem: ReturnedItem = ReturnedItem(itemId, kind)
    val thirdReturnedItem: ReturnedItem = ReturnedItem(itemId, kind)

    describe("when compared with another identical in returned item") {
      it("should be equal following the symmetrical property") {
        returnedItem shouldEqual secondReturnedItem
        secondReturnedItem shouldEqual returnedItem
      }

      it("should be equal following the transitive property") {
        returnedItem shouldEqual secondReturnedItem
        secondReturnedItem shouldEqual thirdReturnedItem
        returnedItem shouldEqual thirdReturnedItem
      }

      it("should be equal following the reflexive property") {
        returnedItem shouldEqual returnedItem
      }

      it("should have the same hash code as the other") {
        returnedItem.## shouldEqual secondReturnedItem.##
      }
    }

    val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
    val inPlaceItem: InPlaceItem = returnedItem.putInPlace
    val inCartItem: InCartItem = inPlaceItem.putInCart(customer)

    describe("when compared with a different type of item but with the same id") {
      it("should be equal") {
        returnedItem shouldBe inPlaceItem
        inPlaceItem shouldBe inCartItem
        inCartItem shouldBe returnedItem
      }
    }

    describe("when compared with anything else") {
      it("should not be equal") {
        returnedItem should not equal 1.0
      }
    }
  }
}
