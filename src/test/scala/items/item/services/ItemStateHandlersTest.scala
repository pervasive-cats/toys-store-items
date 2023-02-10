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
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import items.item.Repository.ItemNotFound
import items.catalogitem.Repository as CatalogItemRepository
import items.catalogitem.Repository.CatalogItemNotFound
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
          .getConfig("ctx")
          .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue()))
      )
    )
    catalogItemRepository = Some(
      CatalogItemRepository(
        ConfigFactory
          .load()
          .getConfig("ctx")
          .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue()))
      )
    )
    val itemCategoryId: ItemCategoryId = ItemCategoryId(614).getOrElse(fail())
    val store: Store = Store(15).getOrElse(fail())
    val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
    val catalogItem: InPlaceCatalogItem =
      catalogItemRepository.getOrElse(fail()).add(itemCategoryId, store, price).getOrElse(fail())
    val itemId: ItemId = ItemId(9000).value
    inPlaceItem = Some(InPlaceItem(itemId, catalogItem))
    itemRepository.getOrElse(fail()).add(inPlaceItem.getOrElse(fail())).getOrElse(fail())
  }

  describe("An item state handler") {
    given givenItemRepository: ItemRepository = itemRepository.getOrElse(fail())
    given givenCatalogItemRepository: CatalogItemRepository = catalogItemRepository.getOrElse(fail())

    describe("when is called on item added to cart event with an existent item") {
      it("should update the database with the given in cart item") {
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val inCartItem = inPlaceItem.getOrElse(fail()).putInCart(customer)
        ItemStateHandlers.onItemAddedToCart(ItemAddedToCart(inCartItem.kind.id, inCartItem.kind.store, inCartItem.id, customer))
        itemRepository
          .getOrElse(fail())
          .findById(inCartItem.id, inCartItem.kind.id, inCartItem.kind.store)
          .value shouldBe inCartItem
      }
    }

    describe("when is called on item added to cart with a non-existent catalog item") {
      it("should not be allowed") {
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val itemCategoryId: ItemCategoryId = ItemCategoryId(431).getOrElse(fail())
        val store: Store = Store(999).getOrElse(fail())
        val price: Price = Price(Amount(99.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: InPlaceCatalogItem =
          InPlaceCatalogItem(CatalogItemId(999).getOrElse(fail()), itemCategoryId, store, price)
        val itemId: ItemId = ItemId(999).value
        val inCartItem: InCartItem = InCartItem(itemId, catalogItem, customer)
        ItemStateHandlers.onItemAddedToCart(ItemAddedToCart(inCartItem.kind.id, inCartItem.kind.store, inCartItem.id, customer))
        itemRepository
          .getOrElse(fail())
          .findById(inCartItem.id, inCartItem.kind.id, inCartItem.kind.store)
          .left
          .value shouldBe CatalogItemNotFound
      }
    }

    describe("when is called on item added to cart with a non-existent item") {
      it("should not be allowed") {
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val inCartItem = InCartItem(ItemId(999).getOrElse(fail()), inPlaceItem.getOrElse(fail()).kind, customer)
        ItemStateHandlers.onItemAddedToCart(ItemAddedToCart(inCartItem.kind.id, inCartItem.kind.store, inCartItem.id, customer))
        itemRepository
          .getOrElse(fail())
          .findById(inCartItem.id, inCartItem.kind.id, inCartItem.kind.store)
          .left
          .value shouldBe ItemNotFound
      }
    }

    describe("when is called on item returned with an existent item") {
      it("should update the database with the given returned item") {
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val inCartItem = inPlaceItem.getOrElse(fail()).putInCart(customer)
        val returnedItem = inCartItem.returnToStore
        ItemStateHandlers.onItemReturned(ItemReturned(returnedItem.kind.id, returnedItem.kind.store, returnedItem.id))
        itemRepository
          .getOrElse(fail())
          .findById(returnedItem.id, returnedItem.kind.id, returnedItem.kind.store)
          .value shouldBe returnedItem
      }
    }

    describe("when is called on item returned with a non-existent catalog item") {
      it("should not be allowed") {
        val itemCategoryId: ItemCategoryId = ItemCategoryId(431).getOrElse(fail())
        val store: Store = Store(999).getOrElse(fail())
        val price: Price = Price(Amount(99.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: InPlaceCatalogItem =
          InPlaceCatalogItem(CatalogItemId(999).getOrElse(fail()), itemCategoryId, store, price)
        val itemId: ItemId = ItemId(999).value
        val returnedItem = ReturnedItem(itemId, catalogItem)
        ItemStateHandlers.onItemReturned(ItemReturned(returnedItem.kind.id, returnedItem.kind.store, returnedItem.id))
        itemRepository
          .getOrElse(fail())
          .findById(returnedItem.id, returnedItem.kind.id, returnedItem.kind.store)
          .left
          .value shouldBe CatalogItemNotFound
      }
    }

    describe("when is called on item returned with a non-existent item") {
      it("should not be allowed") {
        val returnedItem = ReturnedItem(ItemId(999).getOrElse(fail()), inPlaceItem.getOrElse(fail()).kind)
        ItemStateHandlers.onItemReturned(ItemReturned(returnedItem.kind.id, returnedItem.kind.store, returnedItem.id))
        itemRepository
          .getOrElse(fail())
          .findById(returnedItem.id, returnedItem.kind.id, returnedItem.kind.store)
          .left
          .value shouldBe ItemNotFound
      }
    }

    describe("when is called on item put in place with an existent item") {
      it("should update the database with the given in place item") {
        val item = inPlaceItem.getOrElse(fail())
        ItemStateHandlers.onItemPutInPlace(ItemPutInPlace(item.kind.id, item.kind.store, item.id))
        itemRepository.getOrElse(fail()).findById(item.id, item.kind.id, item.kind.store).value shouldBe item
      }
    }

    describe("when is called on item put in place with a non-existent catalog item") {
      it("should not be allowed") {
        val itemCategoryId: ItemCategoryId = ItemCategoryId(431).getOrElse(fail())
        val store: Store = Store(999).getOrElse(fail())
        val price: Price = Price(Amount(99.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: InPlaceCatalogItem =
          InPlaceCatalogItem(CatalogItemId(999).getOrElse(fail()), itemCategoryId, store, price)
        val itemId: ItemId = ItemId(999).value
        val inPlaceItem = InPlaceItem(itemId, catalogItem)
        ItemStateHandlers.onItemPutInPlace(ItemPutInPlace(inPlaceItem.kind.id, inPlaceItem.kind.store, inPlaceItem.id))
        itemRepository
          .getOrElse(fail())
          .findById(inPlaceItem.id, inPlaceItem.kind.id, inPlaceItem.kind.store)
          .left
          .value shouldBe CatalogItemNotFound
      }
    }

    describe("when is called on item put in place with a non-existent item") {
      it("should not be allowed") {
        val item: InPlaceItem = InPlaceItem(ItemId(999).getOrElse(fail()), inPlaceItem.getOrElse(fail()).kind)
        ItemStateHandlers.onItemPutInPlace(ItemPutInPlace(item.kind.id, item.kind.store, item.id))
        itemRepository.getOrElse(fail()).findById(item.id, item.kind.id, item.kind.store).left.value shouldBe ItemNotFound
      }
    }
  }
}
