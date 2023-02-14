/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.services

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.dimafeng.testcontainers.scalatest.TestContainersForAll
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.Failed
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import items.item.Repository.ItemNotFound
import items.catalogitem.Repository as CatalogItemRepository
import items.catalogitem.Repository.CatalogItemNotFound
import items.item.Repository.OperationFailed
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem}
import items.catalogitem.valueobjects.*
import items.item.Repository as ItemRepository
import items.item.domainevents.{ItemAddedToCart, ItemPutInPlace, ItemReturned}
import items.item.entities.InCartItemOps.returnToStore
import items.item.entities.InPlaceItemOps.putInCart
import items.item.entities.ReturnedItemOps.putInPlace
import items.item.entities.{InCartItem, InPlaceItem, ReturnedItem}
import items.item.valueobjects.{Customer, ItemId}
import items.itemcategory.valueobjects.ItemCategoryId

class ItemStateHandlersTest extends AnyFunSpec with TestContainerForAll {

  private val timeout: FiniteDuration = 300.seconds

  override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:15.1"),
    databaseName = "items",
    username = "test",
    password = "test",
    commonJdbcParams = CommonParams(timeout, timeout, Some("items.sql"))
  )

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var itemRepository: Option[ItemRepository] = None

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var catalogItemRepository: Option[CatalogItemRepository] = None

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var inPlaceItem: Option[InPlaceItem] = None

  override def afterContainersStart(containers: Containers): Unit = {
    itemRepository = Some(
      ItemRepository(
        ConfigFactory
          .load()
          .getConfig("repository")
          .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue()))
      )
    )
    catalogItemRepository = Some(
      CatalogItemRepository(
        ConfigFactory
          .load()
          .getConfig("repository")
          .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue()))
      )
    )
    val itemCategoryId: ItemCategoryId = ItemCategoryId(614).getOrElse(fail())
    val store: Store = Store(15).getOrElse(fail())
    val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
    val catalogItem: InPlaceCatalogItem =
      catalogItemRepository.getOrElse(fail()).add(itemCategoryId, store, price).getOrElse(fail())
    val itemId: ItemId = ItemId(9000).getOrElse(fail())
    inPlaceItem = Some(InPlaceItem(itemId, catalogItem))
    itemRepository.getOrElse(fail()).add(inPlaceItem.getOrElse(fail())).getOrElse(fail())
  }

  private given ItemRepository = itemRepository.getOrElse(fail())
  private given CatalogItemRepository = catalogItemRepository.getOrElse(fail())

  describe("The onItemAddedToCart handler") {
    describe("when is called with an existing item") {
      it("should update the database with the new item") {
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val inCartItem = inPlaceItem.getOrElse(fail()).putInCart(customer)
        ItemStateHandlers
          .onItemAddedToCart(ItemAddedToCart(inCartItem.kind.id, inCartItem.kind.store, inCartItem.id, customer))
          .value shouldBe ()
        itemRepository
          .getOrElse(fail())
          .findById(inCartItem.id, inCartItem.kind.id, inCartItem.kind.store)
          .value match {
            case _: InCartItem => succeed
            case _ => fail()
          }
      }
    }

    describe("when is called with a non-existing catalog item") {
      it("should not be allowed") {
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val itemCategoryId: ItemCategoryId = ItemCategoryId(431).getOrElse(fail())
        val store: Store = Store(999).getOrElse(fail())
        val price: Price = Price(Amount(99.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: InPlaceCatalogItem =
          InPlaceCatalogItem(CatalogItemId(999).getOrElse(fail()), itemCategoryId, store, price)
        val itemId: ItemId = ItemId(999).getOrElse(fail())
        val inCartItem: InCartItem = InCartItem(itemId, catalogItem, customer)
        ItemStateHandlers
          .onItemAddedToCart(ItemAddedToCart(inCartItem.kind.id, inCartItem.kind.store, inCartItem.id, customer))
          .left
          .value shouldBe CatalogItemNotFound
      }
    }

    describe("when is called with a non-existing item") {
      it("should not be allowed") {
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val inCartItem = InCartItem(ItemId(999).getOrElse(fail()), inPlaceItem.getOrElse(fail()).kind, customer)
        ItemStateHandlers
          .onItemAddedToCart(ItemAddedToCart(inCartItem.kind.id, inCartItem.kind.store, inCartItem.id, customer))
          .left
          .value shouldBe OperationFailed
      }
    }
  }

  describe("The onItemReturned handler") {
    describe("when is called with an existing item") {
      it("should update the database with the new item") {
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val inCartItem = inPlaceItem.getOrElse(fail()).putInCart(customer)
        val returnedItem = inCartItem.returnToStore
        ItemStateHandlers
          .onItemReturned(ItemReturned(returnedItem.kind.id, returnedItem.kind.store, returnedItem.id))
          .value shouldBe ()
        itemRepository
          .getOrElse(fail())
          .findById(returnedItem.id, returnedItem.kind.id, returnedItem.kind.store)
          .value match {
            case _: ReturnedItem => succeed
            case _ => fail()
          }
      }
    }

    describe("when is called on item returned with a non-existing catalog item") {
      it("should not be allowed") {
        val itemCategoryId: ItemCategoryId = ItemCategoryId(431).getOrElse(fail())
        val store: Store = Store(999).getOrElse(fail())
        val price: Price = Price(Amount(99.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: InPlaceCatalogItem =
          InPlaceCatalogItem(CatalogItemId(999).getOrElse(fail()), itemCategoryId, store, price)
        val itemId: ItemId = ItemId(999).getOrElse(fail())
        val returnedItem = ReturnedItem(itemId, catalogItem)
        ItemStateHandlers
          .onItemReturned(ItemReturned(returnedItem.kind.id, returnedItem.kind.store, returnedItem.id))
          .left
          .value shouldBe CatalogItemNotFound
      }
    }

    describe("when is called on item returned with a non-existing item") {
      it("should not be allowed") {
        val returnedItem = ReturnedItem(ItemId(999).getOrElse(fail()), inPlaceItem.getOrElse(fail()).kind)
        ItemStateHandlers
          .onItemReturned(ItemReturned(returnedItem.kind.id, returnedItem.kind.store, returnedItem.id))
          .left
          .value shouldBe OperationFailed
      }
    }
  }

  describe("The onItemPutInPlace method") {
    describe("when is called with an existing item") {
      it("should update the database with the new item") {
        val item = inPlaceItem.getOrElse(fail())
        ItemStateHandlers.onItemPutInPlace(ItemPutInPlace(item.kind.id, item.kind.store, item.id)).value shouldBe ()
        itemRepository.getOrElse(fail()).findById(item.id, item.kind.id, item.kind.store).value shouldBe item
      }
    }

    describe("when is called with a non-existing catalog item") {
      it("should not be allowed") {
        val itemCategoryId: ItemCategoryId = ItemCategoryId(431).getOrElse(fail())
        val store: Store = Store(999).getOrElse(fail())
        val price: Price = Price(Amount(99.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: InPlaceCatalogItem =
          InPlaceCatalogItem(CatalogItemId(999).getOrElse(fail()), itemCategoryId, store, price)
        val itemId: ItemId = ItemId(999).getOrElse(fail())
        val inPlaceItem = InPlaceItem(itemId, catalogItem)
        ItemStateHandlers
          .onItemPutInPlace(ItemPutInPlace(inPlaceItem.kind.id, inPlaceItem.kind.store, inPlaceItem.id))
          .left
          .value shouldBe CatalogItemNotFound
      }
    }

    describe("when is called with a non-existing item") {
      it("should not be allowed") {
        val item: InPlaceItem = InPlaceItem(ItemId(999).getOrElse(fail()), inPlaceItem.getOrElse(fail()).kind)
        ItemStateHandlers
          .onItemPutInPlace(ItemPutInPlace(item.kind.id, item.kind.store, item.id))
          .left
          .value shouldBe OperationFailed
      }
    }
  }
}
