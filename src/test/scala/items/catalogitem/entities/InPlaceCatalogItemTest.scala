/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.entities

import eu.timepit.refined.auto.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

import items.catalogitem.entities.InPlaceCatalogItem.*
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.entities.CatalogItemOps.{updated, lift}
import items.catalogitem.valueobjects.*
import items.itemcategory.valueobjects.ItemCategoryId

class InPlaceCatalogItemTest extends AnyFunSpec {

  private val id: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
  private val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
  private val store: Store = Store(13).getOrElse(fail())
  private val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
  private val inPlaceCatalogItem: InPlaceCatalogItem = InPlaceCatalogItem(id, category, store, price)

  describe("An in place catalog item") {
    describe("when created with an id, a category, a store and a price") {
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
        inPlaceCatalogItem.updated(newPrice).price shouldBe newPrice
      }
    }

    val secondCatalogItem: InPlaceCatalogItem = InPlaceCatalogItem(id, category, store, price)
    val thirdCatalogItem: InPlaceCatalogItem = InPlaceCatalogItem(id, category, store, price)

    describe("when compared with another identical catalog item") {
      it("should be equal following the symmetrical property") {
        inPlaceCatalogItem shouldEqual secondCatalogItem
        secondCatalogItem shouldEqual inPlaceCatalogItem
      }

      it("should be equal following the transitive property") {
        inPlaceCatalogItem shouldEqual secondCatalogItem
        secondCatalogItem shouldEqual thirdCatalogItem
        inPlaceCatalogItem shouldEqual thirdCatalogItem
      }

      it("should be equal following the reflexive property") {
        inPlaceCatalogItem shouldEqual inPlaceCatalogItem
      }

      it("should have the same hash code as the other") {
        inPlaceCatalogItem.## shouldEqual secondCatalogItem.##
      }
    }

    describe("when compared with anything else") {
      it("should not be equal") {
        inPlaceCatalogItem should not equal 1.0
      }
    }

    describe("when it is lifted") {
      it("should contain the same values and the count should be 1") {
        val liftedCatalogItem: LiftedCatalogItem = inPlaceCatalogItem.lift.getOrElse(fail())
        liftedCatalogItem.id shouldBe inPlaceCatalogItem.id
        liftedCatalogItem.category shouldBe inPlaceCatalogItem.category
        liftedCatalogItem.store shouldBe inPlaceCatalogItem.store
        liftedCatalogItem.price shouldBe inPlaceCatalogItem.price
        (liftedCatalogItem.count.value: Long) shouldBe 1L
      }
    }
  }
}
