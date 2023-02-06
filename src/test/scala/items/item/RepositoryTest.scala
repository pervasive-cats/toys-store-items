package io.github.pervasivecats
package items.item

import io.github.pervasivecats.items.catalogitem.entities.CatalogItem
import io.github.pervasivecats.items.catalogitem.valueobjects.{CatalogItemId, Store}
import io.github.pervasivecats.items.item.Repository.*
import io.github.pervasivecats.items.item.valueobjects.{Customer, ItemId}
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId
import items.catalogitem.Repository as CatalogItemRepository
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.item.entities.*
import items.item.entities.InCartItemOps.returnToStore
import items.item.entities.InPlaceItemOps.putInCart
import io.github.pervasivecats.items.Validated
import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import scala.concurrent.duration.{FiniteDuration, SECONDS}
import scala.language.postfixOps

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

    for {
      category <- ItemCategoryId(614)
      store <- Store(15)
      price <- for {
        amount <- Amount(19.99)
      } yield Price(amount, Currency.withName("EUR"))
    } yield catalogItemRepositoryOpt.getOrElse(fail()).add(category, store, price)

  }

  describe("An Item") {
    given catalogItemRepository: CatalogItemRepository = catalogItemRepositoryOpt.getOrElse(fail())
    
    describe("after being added") {
      it("should be present in the database") {
        val db = repository.getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(0).value
        val store: Store = Store(15).value
        val customer: Customer = Customer("elena@gmail.com").value
        val inCartItem: Item = db.add(catalogItemId, customer, store).value
        db.findById(inCartItem.id, catalogItemId, store).value
        db.remove(inCartItem.id, catalogItemId, store).value
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

    describe("after being added and then deleted") {
      it("should not be present in database") {
        val db = repository.getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(0).value
        val store: Store = Store(15).value
        val customer: Customer = Customer("elena@gmail.com").value
        val inCartItem: Item = db.add(catalogItemId, customer, store).value
        db.remove(inCartItem.id, catalogItemId, store).value
        db.findById(inCartItem.id, catalogItemId, store).left.value shouldBe ItemNotFound
      }
    }

    describe("after being removed but it were never added in the first place") {
      it("should not be allowed") {
        val db: Repository = repository.getOrElse(fail())
        val itemId: ItemId = ItemId(144).value
        val catalogItemId: CatalogItemId = CatalogItemId(0).value
        val store: Store = Store(15).value
        db.remove(itemId, catalogItemId, store).left.value shouldBe OperationFailed
      }
    }

    describe("after being added and then it data gets updated") {
      it("should show the update") {
        val db = repository.getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(0).value
        val store: Store = Store(15).value
        val customer: Customer = Customer("elena@gmail.com").value
        val inPlaceItem: InPlaceItem = db.add(catalogItemId, customer, store).value
        val inCartItem: InCartItem = inPlaceItem.putInCart(customer)
        db.update(inCartItem, catalogItemId, store)
        db.findById(inCartItem.id, catalogItemId, store).value match {
          case _: InCartItem => true
          case _ => false
        } shouldBe true
        db.remove(inPlaceItem.id, catalogItemId, store).value
      }
    }

    describe("when it data gets updated but they were never added in the first place") {
      it("should not be allowed") {
        val db: Repository = repository.getOrElse(fail())
        val itemId: ItemId = ItemId(144).value
        val category: ItemCategoryId = ItemCategoryId(35).value
        val store: Store = Store(15).value
        val catalogItemId: CatalogItemId = CatalogItemId(0).value
        val price: Price = Price(Amount(19.99).value, Currency.withName("EUR"))
        val kind: LiftedCatalogItem = LiftedCatalogItem(catalogItemId, category, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, kind)
        db.update(inPlaceItem, catalogItemId, store).left.value shouldBe OperationFailed
      }
    }

    describe("if never registered, while searching for all returned items") {
      it("should not be in the database") {
        val db: Repository = repository.getOrElse(fail())
        val empty: Int = 0
        db.findAllReturned().value.size shouldBe empty
      }
    }

    describe("if added twice, while searching for all returned items") {
      it("should be in the database along with its clone") {
        val db: Repository = repository.getOrElse(fail())
        val empty: Int = 0
        val one: Int = 1
        val two: Int = 2
        val catalogItemId: CatalogItemId = CatalogItemId(0).value
        val store: Store = Store(15).value
        val customer: Customer = Customer("elena@gmail.com").value

        val inPlaceItemA: Item = db.add(catalogItemId, customer, store).value
        inPlaceItemA match {
          case elem: InPlaceItem =>
            val inCartItem: InCartItem = elem.putInCart(customer)
            val returnedItem: ReturnedItem = inCartItem.returnToStore
            db.update(returnedItem, catalogItemId, store)
        }
        db.findAllReturned().value.size shouldBe one

        val inPlaceItemB: Item = db.add(catalogItemId, customer, store).value
        inPlaceItemB match {
          case elem: InPlaceItem =>
            val inCartItem: InCartItem = elem.putInCart(customer)
            val returnedItem: ReturnedItem = inCartItem.returnToStore
            db.update(returnedItem, catalogItemId, store)
        }
        db.findAllReturned().value.size shouldBe two

        db.remove(inPlaceItemA.id, catalogItemId, store)
        db.remove(inPlaceItemB.id, catalogItemId, store)
        db.findAllReturned().value.size shouldBe empty
      }
    }
  }
}
