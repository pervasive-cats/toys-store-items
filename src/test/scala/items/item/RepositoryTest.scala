/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import io.github.pervasivecats.Validated

import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import eu.timepit.refined.auto.given
import io.getquill.JdbcContextConfig
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import items.RepositoryOperationFailed
import items.catalogitem.entities.CatalogItem
import items.catalogitem.valueobjects.{CatalogItemId, Store}
import items.item.Repository.*
import items.item.valueobjects.{Customer, ItemId}
import items.itemcategory.valueobjects.ItemCategoryId
import items.catalogitem.Repository as CatalogItemRepository
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.item.entities.*
import items.item.entities.InCartItemOps.returnToStore
import items.item.entities.InPlaceItemOps.putInCart

class RepositoryTest extends AnyFunSpec with TestContainerForAll {

  private val timeout: FiniteDuration = 300.seconds

  override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:15.1"),
    databaseName = "items",
    username = "test",
    password = "test",
    commonJdbcParams = CommonParams(timeout, timeout, Some("items.sql"))
  )

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var repository: Option[Repository] = None

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var catalogItemRepositoryOpt: Option[CatalogItemRepository] = None

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var catalogItemId: Option[CatalogItemId] = None

  override def afterContainersStart(containers: Containers): Unit = {
    repository = Some(
      Repository(
        JdbcContextConfig(
          ConfigFactory
            .load()
            .getConfig("repository")
            .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue()))
        ).dataSource
      )
    )
    catalogItemRepositoryOpt = Some(
      CatalogItemRepository(
        JdbcContextConfig(
          ConfigFactory
            .load()
            .getConfig("repository")
            .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue()))
        ).dataSource
      )
    )
    val category: ItemCategoryId = ItemCategoryId(614).getOrElse(fail())
    val store: Store = Store(15).getOrElse(fail())
    val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
    catalogItemId = Some(catalogItemRepositoryOpt.getOrElse(fail()).add(category, store, price).getOrElse(fail()).id)
  }

  describe("An Item") {
    given catalogItemRepository: CatalogItemRepository = catalogItemRepositoryOpt.getOrElse(fail())

    describe("after being added") {
      it("should be present in the database") {
        val db = repository.getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        val itemId: ItemId = ItemId(9000).getOrElse(fail())
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, catalogItem)
        db.add(inPlaceItem).getOrElse(fail())
        val item: Item = db.findById(inPlaceItem.id, catalogItemId.getOrElse(fail()), store).getOrElse(fail())
        item.id shouldBe itemId
        item.kind shouldBe catalogItem
        db.remove(inPlaceItem).getOrElse(fail())
      }
    }

    describe("if never added") {
      it("should not be present") {
        val db: Repository = repository.getOrElse(fail())
        val itemId: ItemId = ItemId(144).getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(0).getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        db.findById(itemId, catalogItemId, store).left.value shouldBe ItemNotFound
      }
    }

    describe("after being added with a existing id") {
      it("should not be present in database") {
        val db = repository.getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        val itemId: ItemId = ItemId(9000).getOrElse(fail())
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, catalogItem)
        db.add(inPlaceItem).getOrElse(fail())
        db.add(inPlaceItem).left.value shouldBe ItemAlreadyPresent
        db.remove(inPlaceItem).getOrElse(fail())
      }
    }

    describe("after being added and then deleted") {
      it("should not be present in database") {
        val db = repository.getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        val itemId: ItemId = ItemId(9000).getOrElse(fail())
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, catalogItem)
        db.add(inPlaceItem).getOrElse(fail())
        db.remove(inPlaceItem).getOrElse(fail())
        db.findById(inPlaceItem.id, catalogItemId.getOrElse(fail()), store).left.value shouldBe ItemNotFound
      }
    }

    describe("after being removed but it were never added in the first place") {
      it("should not be allowed") {
        val db: Repository = repository.getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        val itemId: ItemId = ItemId(9000).getOrElse(fail())
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, catalogItem)
        db.remove(inPlaceItem).left.value shouldBe RepositoryOperationFailed
      }
    }

    describe("after being added and then it data gets updated many times") {
      it("should show the update") {
        val db = repository.getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        val itemId: ItemId = ItemId(9000).getOrElse(fail())
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, catalogItem)
        db.add(inPlaceItem).getOrElse(fail())

        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val inCartItem: InCartItem = inPlaceItem.putInCart(customer)
        db.update(inCartItem).getOrElse(fail())
        db.findById(inCartItem.id, inCartItem.kind.id, inCartItem.kind.store).value match {
          case item: InCartItem => item shouldBe inCartItem
          case _ => fail()
        }

        val returnedItem: ReturnedItem = inCartItem.returnToStore
        db.update(returnedItem).getOrElse(fail())
        db.findById(returnedItem.id, returnedItem.kind.id, returnedItem.kind.store).value match {
          case item: ReturnedItem => item shouldBe returnedItem
          case _ => fail()
        }

        db.update(inPlaceItem).getOrElse(fail())
        db.findById(inPlaceItem.id, inPlaceItem.kind.id, inPlaceItem.kind.store).value match {
          case item: InPlaceItem => item shouldBe inPlaceItem
          case _ => fail()
        }

        db.remove(inCartItem).getOrElse(fail())
      }
    }

    describe("when its data gets updated but it was never added in the first place") {
      it("should not be allowed") {
        val db: Repository = repository.getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        val itemId: ItemId = ItemId(9000).getOrElse(fail())
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, catalogItem)
        db.update(inPlaceItem).left.value shouldBe RepositoryOperationFailed
      }
    }

    describe("if never added, while searching for all returned items") {
      it("should not be in the database") {
        val db: Repository = repository.getOrElse(fail())
        db.findAllReturned().value.size shouldBe 0
      }
    }

    describe("if added twice, while searching for all returned items") {
      it("should be in the database along with its clone") {
        val db: Repository = repository.getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        db.findAllReturned().value.size shouldBe 0

        val firstItemId: ItemId = ItemId(9000).getOrElse(fail())
        val firstInPlaceItem: InPlaceItem = InPlaceItem(firstItemId, catalogItem)
        db.add(firstInPlaceItem).getOrElse(fail())
        val firstInCartItem: InCartItem = firstInPlaceItem.putInCart(customer)
        val firstReturnedItem: ReturnedItem = firstInCartItem.returnToStore
        db.update(firstReturnedItem).getOrElse(fail())
        db.findAllReturned().value.size shouldBe 1

        val secondItemId: ItemId = ItemId(9001).getOrElse(fail())
        val secondInPlaceItem: InPlaceItem = InPlaceItem(secondItemId, catalogItem)
        db.add(secondInPlaceItem).getOrElse(fail())
        val secondInCartItem: InCartItem = secondInPlaceItem.putInCart(customer)
        val secondReturnedItem: ReturnedItem = secondInCartItem.returnToStore
        db.update(secondReturnedItem).getOrElse(fail())
        db.findAllReturned().value.size shouldBe 2

        db.remove(firstInPlaceItem).getOrElse(fail())
        db.remove(secondInPlaceItem).getOrElse(fail())
        db.findAllReturned().value.size shouldBe 0
      }
    }
  }
}
