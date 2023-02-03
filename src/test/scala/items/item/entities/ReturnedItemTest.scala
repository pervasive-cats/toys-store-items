package io.github.pervasivecats
package items.item.entities

import scala.language.postfixOps

import io.github.pervasivecats.items.catalogitem.entities.CatalogItem
import io.github.pervasivecats.items.item.entities.InCartItemOps.returnToStore
import io.github.pervasivecats.items.item.valueobjects.ItemId

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

import items.catalogitem.entities.CatalogItemOps.updated
import items.catalogitem.entities.InPlaceCatalogItem.*
import items.catalogitem.entities.InPlaceCatalogItemOps.lift
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.item.valueobjects.Customer
import items.itemcategory.valueobjects.ItemCategoryId
import items.item.entities.ReturnedItemOps.putInPlace

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

    describe("when its putted in place") {
      it("should contain the same value") {
        val inPlaceItem: InPlaceItem = returnedItem.putInPlace
        inPlaceItem.id shouldBe returnedItem.id
        inPlaceItem.kind shouldBe returnedItem.kind
      }
    }

    val secondReturnedItem: ReturnedItem = ReturnedItem(itemId, kind)
    val thirdReturnedItem: ReturnedItem = ReturnedItem(itemId, kind)

    describe("when compared with another identical in retuned item") {
      it("should be equal fllowing the symmetrical property") {
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
  }
}
