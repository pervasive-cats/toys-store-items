package io.github.pervasivecats
package items.item

import io.github.pervasivecats.items.catalogitem.entities.CatalogItem
import io.github.pervasivecats.items.catalogitem.valueobjects.{CatalogItemId, Store}
import io.github.pervasivecats.items.item.Repository.*
import items.item.entities.InPlaceItemOps.putInCart
import io.github.pervasivecats.items.item.valueobjects.{Customer, ItemId}
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.item.entities.*

import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
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

  override def afterContainersStart(containers: Containers): Unit =
    repository = Some(Repository.withPort(containers.container.getFirstMappedPort.intValue()))

  describe("An Item") {
    describe("after being added") {
      it("should be present in the database") {
        val db = repository.getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(345).getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val inCartItem: Item = db.add(catalogItemId, customer, store).getOrElse(fail())
        db.findById(inCartItem.id, catalogItemId, store).getOrElse(fail())
        db.remove(inCartItem.id, catalogItemId, store).getOrElse(fail())
      }
    }

    describe("if never added") {
      it("should not be present") {
        val db: Repository = repository.getOrElse(fail())
        val itemId: ItemId = ItemId(144).getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(345).getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        db.findById(itemId, catalogItemId, store).left.value shouldBe ItemNotFound
      }
    }

    describe("after being added and then deleted") {
      it("should not be present in database") {
        val db = repository.getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(345).getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val inCartItem: Item = db.add(catalogItemId, customer, store).getOrElse(fail())
        db.remove(inCartItem.id, catalogItemId, store).getOrElse(fail())
        db.findById(inCartItem.id, catalogItemId, store).left.value shouldBe ItemNotFound
      }
    }

    describe("after being removed but it were never added in the first place") {
      it("should not be allowed") {
        val db: Repository = repository.getOrElse(fail())
        val itemId: ItemId = ItemId(144).getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(345).getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        db.remove(itemId, catalogItemId, store).left.value shouldBe OperationFailed
      }
    }

    describe("after being added and then it data gets updated") {
      it("should show the update") {
        val db = repository.getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(345).getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val inPlaceItem: InPlaceItem = db.add(catalogItemId, customer, store).getOrElse(fail())
        val inCartItem: InCartItem = inPlaceItem.putInCart(customer)
        db.update(inCartItem, catalogItemId, store)
        db.findById(inCartItem.id, catalogItemId, store).getOrElse(fail()) match {
          case _: InCartItem => true
          case _ => false
        } shouldBe true
        db.remove(inPlaceItem.id, catalogItemId, store).getOrElse(fail())
      }
    }

    describe("when it data gets updated but they were never added in the first place") {
      it("should not be allowed") {
        val db: Repository = repository.getOrElse(fail())
        val itemId: ItemId = ItemId(144).getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(345).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val kind: LiftedCatalogItem = LiftedCatalogItem(catalogItemId, category, store, price)
        val inPlaceItem: InPlaceItem = InPlaceItem(itemId, kind)
        db.update(inPlaceItem, catalogItemId, store).left.value shouldBe OperationFailed
      }
    }
    
    



  }
}
