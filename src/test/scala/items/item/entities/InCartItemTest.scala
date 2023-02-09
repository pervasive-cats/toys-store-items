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
import items.item.valueobjects.Customer
import items.itemcategory.valueobjects.ItemCategoryId
import items.item.entities.InCartItemOps.returnToStore
import items.item.entities.ReturnedItemOps.putInPlace

class InCartItemTest extends AnyFunSpec {

  private val itemId: ItemId = ItemId(9000).getOrElse(fail())

  private val catalogItemId: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
  private val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
  private val store: Store = Store(13).getOrElse(fail())
  private val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
  private val kind: LiftedCatalogItem = LiftedCatalogItem(catalogItemId, category, store, price)

  private val customer: Customer = Customer("addr.3ss.!@email.test.com").getOrElse(fail())

  private val inCartItem: InCartItem = InCartItem(itemId, kind, customer)

  describe("An in cart item") {
    describe("when created with an id, a kind and a customer") {
      it("should contain them") {
        inCartItem.id shouldBe itemId
        inCartItem.kind shouldBe kind
        inCartItem.customer shouldBe customer
      }
    }

    describe("when its returned to the store") {
      it("should contain the same value") {
        val returnedItem: ReturnedItem = inCartItem.returnToStore
        returnedItem.id shouldBe inCartItem.id
        returnedItem.kind shouldBe inCartItem.kind
      }
    }

    val secondInCartItem: InCartItem = InCartItem(itemId, kind, customer)
    val thirdInCartItem: InCartItem = InCartItem(itemId, kind, customer)

    describe("when compared with another identical in cart item") {
      it("should be equal following the symmetrical property") {
        inCartItem shouldEqual secondInCartItem
        secondInCartItem shouldEqual inCartItem
      }

      it("should be equal following the transitive property") {
        inCartItem shouldEqual secondInCartItem
        secondInCartItem shouldEqual thirdInCartItem
        inCartItem shouldEqual thirdInCartItem
      }

      it("should be equal following the reflexive property") {
        inCartItem shouldEqual inCartItem
      }

      it("should have the same hash code as the other") {
        inCartItem.## shouldEqual secondInCartItem.##
      }
    }

    val returnedItem: ReturnedItem = inCartItem.returnToStore
    val inPlaceItem: InPlaceItem = returnedItem.putInPlace

    describe("when compared with a different type of item but with the same id") {
      it("should be equal") {
        inCartItem shouldBe returnedItem
        returnedItem shouldBe inPlaceItem
        inPlaceItem shouldBe inCartItem
      }
    }

    describe("when compared with anything else") {
      it("should not be equal") {
        inCartItem should not equal 1.0
      }
    }
  }
}
