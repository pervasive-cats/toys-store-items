package io.github.pervasivecats
package items.item

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS
import scala.language.postfixOps
import io.github.pervasivecats.items.Validated
import io.github.pervasivecats.items.catalogitem.entities.CatalogItem
import io.github.pervasivecats.items.catalogitem.valueobjects.CatalogItemId
import io.github.pervasivecats.items.catalogitem.valueobjects.Store
import io.github.pervasivecats.items.item.Repository.*
import io.github.pervasivecats.items.item.valueobjects.Customer
import io.github.pervasivecats.items.item.valueobjects.ItemId
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId

import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName
import items.catalogitem.Repository as CatalogItemRepository
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.item.entities.*
import items.item.entities.InCartItemOps.returnToStore
import items.item.entities.InPlaceItemOps.putInCart

class RepositoryTest extends AnyFunSpec with TestContainerForAll {

  private val timeout: FiniteDuration = FiniteDuration(300, SECONDS)

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
        ConfigFactory
          .load()
          .getConfig("ctx")
          .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue()))
      )
    )
    catalogItemRepositoryOpt = Some(
      CatalogItemRepository(
        ConfigFactory
          .load()
          .getConfig("ctx")
          .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue()))
      )
    )

    val category: ItemCategoryId = ItemCategoryId(614).getOrElse(fail())
    val store: Store = Store(15).getOrElse(fail())
    val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
    catalogItemId= Some(catalogItemRepositoryOpt.getOrElse(fail()).add(category, store, price).getOrElse(fail()).id)
  }

  describe("An Item") {
    given catalogItemRepository: CatalogItemRepository = catalogItemRepositoryOpt.getOrElse(fail())

    describe("after being added") {
      it("should be present in the database") {
        val db = repository.getOrElse(fail())
        val store: Store = Store(15).value
        val itemId: ItemId = ItemId(9000).value
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).value
        val price: Price = Price(Amount(19.99).value, Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, catalogItem)
        db.add(inPlaceItem).getOrElse(fail())
        db.findById(inPlaceItem.id, catalogItemId.getOrElse(fail()), store).getOrElse(fail())
        db.remove(inPlaceItem).value
      }
    }

    describe("if never added") {
      it("should not be present") {
        val db: Repository = repository.getOrElse(fail())
        val itemId: ItemId = ItemId(144).value
        val catalogItemId: CatalogItemId = CatalogItemId(0).value
        val store: Store = Store(15).value
        db.findById(itemId, catalogItemId, store).left.value shouldBe ItemNotFound
      }
    }

    describe("after being added with a existent id") {
      it("should not be present in database") {
        val db = repository.getOrElse(fail())
        val store: Store = Store(15).value
        val itemId: ItemId = ItemId(9000).value
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).value
        val price: Price = Price(Amount(19.99).value, Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, catalogItem)
        db.add(inPlaceItem).getOrElse(fail())
        db.add(inPlaceItem).left.value shouldBe ItemAlreadyPresent
        db.remove(inPlaceItem)
      }
    }

    describe("after being added and then deleted") {
      it("should not be present in database") {
        val db = repository.getOrElse(fail())
        val store: Store = Store(15).value
        val itemId: ItemId = ItemId(9000).value
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).value
        val price: Price = Price(Amount(19.99).value, Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, catalogItem)
        db.add(inPlaceItem).getOrElse(fail())
        db.remove(inPlaceItem).value
        db.findById(inPlaceItem.id, catalogItemId.getOrElse(fail()), store).left.value shouldBe ItemNotFound
      }
    }

    describe("after being removed but it were never added in the first place") {
      it("should not be allowed") {
        val db: Repository = repository.getOrElse(fail())
        val store: Store = Store(15).value
        val itemId: ItemId = ItemId(9000).value
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).value
        val price: Price = Price(Amount(19.99).value, Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, catalogItem)
        db.remove(inPlaceItem).left.value shouldBe OperationFailed
      }
    }

    describe("after being added and then it data gets updated") {
      it("should show the update") {
        val db = repository.getOrElse(fail())
        val store: Store = Store(15).value
        val itemId: ItemId = ItemId(9000).value
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).value
        val price: Price = Price(Amount(19.99).value, Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, catalogItem)
        db.add(inPlaceItem).value
        val customer: Customer = Customer("elena@gmail.com").value
        val inCartItem: InCartItem = inPlaceItem.putInCart(customer)
        db.update(inCartItem).value
        db.findById(inCartItem.id, inCartItem.kind.id, inCartItem.kind.store).value match {
          case item: InCartItem => item.customer shouldBe customer
        }
        db.remove(inCartItem)
      }
    }

    describe("when it data gets updated but they were never added in the first place") {
      it("should not be allowed") {
        val db: Repository = repository.getOrElse(fail())
        val store: Store = Store(15).value
        val itemId: ItemId = ItemId(9000).value
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).value
        val price: Price = Price(Amount(19.99).value, Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, catalogItem)
        db.update(inPlaceItem).left.value shouldBe OperationFailed
      }
    }

    describe("if never registered, while searching for all returned items") {
      it("should not be in the database") {
        val db: Repository = repository.getOrElse(fail())
        val empty: Int = 0
        db.findAllReturned().value.size shouldBe empty
      }
    }

    /*describe("if added twice, while searching for all returned items") {
      it("should be in the database along with its clone") {
        val db: Repository = repository.getOrElse(fail())
        val empty: Int = 0
        val one: Int = 1
        val two: Int = 2

        val store: Store = Store(15).value
        val itemCategoryId: ItemCategoryId = ItemCategoryId(614).value
        val price: Price = Price(Amount(19.99).value, Currency.withName("EUR"))
        val id: CatalogItemId = catalogItemId.getOrElse(fail())
        val catalogItem: CatalogItem = InPlaceCatalogItem(id, itemCategoryId, store, price)
        val customer: Customer = Customer("elena@gmail.com").value
        db.findAllReturned().value.size shouldBe empty

        val firstItemId: ItemId = ItemId(9000).value
        val firstInPlaceItem: InPlaceItem = InPlaceItem(firstItemId, catalogItem)
        db.add(firstInPlaceItem).value
        val firstInCartItem: InCartItem = firstInPlaceItem.putInCart(customer)
        val returnedItem: ReturnedItem =
        db.update(firstInCartItem).value
        db.findAllReturned().value.size shouldBe one

        /*val secondItemId: ItemId = ItemId(9001).value
        val secondInPlaceItem: InPlaceItem = InPlaceItem(secondItemId, catalogItem)
        db.add(secondInPlaceItem)
        val secondInCartItem: InCartItem = secondInPlaceItem.putInCart(customer)
        db.update(secondInCartItem).value
        db.findAllReturned().value.size shouldBe two

        db.remove(firstInPlaceItem)
        db.remove(secondInPlaceItem)
        db.findAllReturned().value.size shouldBe empty*/
      }
    }*/
  }
}
