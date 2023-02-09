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
import items.catalogitem.entities.LiftedCatalogItemOps.{putInPlace, updated}
import items.catalogitem.valueobjects.*
import items.itemcategory.valueobjects.ItemCategoryId

class LiftedCatalogItemTest extends AnyFunSpec {

  private val id: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
  private val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
  private val store: Store = Store(13).getOrElse(fail())
  private val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
  private val count: Count = Count(2).getOrElse(fail())
  private val liftedCatalogItem: LiftedCatalogItem = LiftedCatalogItem(id, category, store, price, count)

  describe("A lifted catalog item") {
    describe("when created with a id, a category, a store and a price") {
      it("should contain them") {
        liftedCatalogItem.id shouldBe id
        liftedCatalogItem.category shouldBe category
        liftedCatalogItem.store shouldBe store
        liftedCatalogItem.price shouldBe price
        liftedCatalogItem.count shouldBe count
      }
    }

    describe("when updated with a new price") {
      it("should contain that") {
        val newPrice: Price = Price(Amount(14.99).getOrElse(fail()), Currency.withName("EUR"))
        liftedCatalogItem.updated(price = newPrice).price shouldBe newPrice
      }
    }

    val secondCatalogItem: LiftedCatalogItem = LiftedCatalogItem(id, category, store, price, count)
    val thirdCatalogItem: LiftedCatalogItem = LiftedCatalogItem(id, category, store, price, count)

    describe("when compared with another identical catalog item") {
      it("should be equal following the symmetrical property") {
        liftedCatalogItem shouldEqual secondCatalogItem
        secondCatalogItem shouldEqual liftedCatalogItem
      }

      it("should be equal following the transitive property") {
        liftedCatalogItem shouldEqual secondCatalogItem
        secondCatalogItem shouldEqual thirdCatalogItem
        liftedCatalogItem shouldEqual thirdCatalogItem
      }

      it("should be equal following the reflexive property") {
        liftedCatalogItem shouldEqual liftedCatalogItem
      }

      it("should have the same hash code as the other") {
        liftedCatalogItem.## shouldEqual secondCatalogItem.##
      }
    }

    describe("when compared with anything else") {
      it("should not be equal") {
        liftedCatalogItem should not equal 1.0
      }
    }

    describe("when it is placed on the shelf once") {
      it("should contain the same values") {
        val catalogItem: CatalogItem = liftedCatalogItem.putInPlace.getOrElse(fail())
        catalogItem.id shouldBe liftedCatalogItem.id
        catalogItem.category shouldBe liftedCatalogItem.category
        catalogItem.store shouldBe liftedCatalogItem.store
        catalogItem.price shouldBe liftedCatalogItem.price
        catalogItem match {
          case item: LiftedCatalogItem =>
            (item.count.value: Long) shouldBe (liftedCatalogItem.count.value - 1L)
            item.putInPlace.getOrElse(fail()) match {
              case item: InPlaceCatalogItem =>
                item.id shouldBe liftedCatalogItem.id
                item.category shouldBe liftedCatalogItem.category
                item.store shouldBe liftedCatalogItem.store
                item.price shouldBe liftedCatalogItem.price
              case _ => fail()
            }
          case _ => fail()
        }

      }
    }
  }
}
