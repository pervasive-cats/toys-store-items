/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.entities

import io.github.pervasivecats.items.catalogitem.entities.CatalogItem
import io.github.pervasivecats.items.item.entities.InCartItemOps.returnToStore
import io.github.pervasivecats.items.item.valueobjects.ItemId

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

import items.catalogitem.entities.InPlaceCatalogItem.*
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.item.entities.InPlaceItemOps.putInCart
import items.item.entities.InCartItemOps.returnToStore
import items.item.valueobjects.Customer
import items.itemcategory.valueobjects.ItemCategoryId

class InPlaceItemTest extends AnyFunSpec {

  private val itemId: ItemId = ItemId(9000).getOrElse(fail())

  private val catalogItemId: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
  private val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
  private val store: Store = Store(13).getOrElse(fail())
  private val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
  private val count: Count = Count(1).getOrElse(fail())
  private val kind: LiftedCatalogItem = LiftedCatalogItem(catalogItemId, category, store, price, count)

  private val inPlaceItem: InPlaceItem = InPlaceItem(itemId, kind)

  describe("An in place item") {
    describe("when created with an id and a kind") {
      it("should contain them") {
        inPlaceItem.id shouldBe itemId
        inPlaceItem.kind shouldBe kind
      }
    }

    describe("when it is put in cart by a customer") {
      it("should contain the same value and the customer") {
        val customer: Customer = Customer("addr.3ss.!@email.test.com").getOrElse(fail())
        val inCartItem: InCartItem = inPlaceItem.putInCart(customer)
        inCartItem.id shouldBe inPlaceItem.id
        inCartItem.kind shouldBe inPlaceItem.kind
        inCartItem.customer shouldBe customer
      }
    }

    val secondInPlaceItem: InPlaceItem = InPlaceItem(itemId, kind)
    val thirdInPlaceItem: InPlaceItem = InPlaceItem(itemId, kind)

    describe("when compared with another identical in place item") {
      it("should be equal following the symmetrical property") {
        inPlaceItem shouldEqual secondInPlaceItem
        secondInPlaceItem shouldEqual inPlaceItem
      }

      it("should be equal following the transitive property") {
        inPlaceItem shouldEqual secondInPlaceItem
        secondInPlaceItem shouldEqual thirdInPlaceItem
        inPlaceItem shouldEqual thirdInPlaceItem
      }

      it("should be equal following the reflexive property") {
        inPlaceItem shouldEqual inPlaceItem
      }

      it("should have the same hash code as the other") {
        inPlaceItem.## shouldEqual secondInPlaceItem.##
      }
    }

    val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
    val inCartItem: InCartItem = inPlaceItem.putInCart(customer)
    val returnedItem: ReturnedItem = inCartItem.returnToStore

    describe("when compared with a different type of item but with the same id") {
      it("should be equal") {
        inPlaceItem shouldBe inCartItem
        inCartItem shouldBe returnedItem
        returnedItem shouldBe inPlaceItem
      }
    }

    describe("when compared with anything else") {
      it("should not be equal") {
        inPlaceItem should not equal 1.0
      }
    }
  }
}
