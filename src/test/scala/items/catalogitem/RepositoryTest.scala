package io.github.pervasivecats
package items.catalogitem

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.getquill.autoQuote
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import items.Validated
import items.catalogitem.Repository.{CatalogItemNotFound, OperationFailed}
import items.catalogitem.entities.InPlaceCatalogItemOps.lift
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.itemcategory.valueobjects.ItemCategoryId

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

  override def afterContainersStart(containers: Containers): Unit =
    repository = Some(
      Repository(
        ConfigFactory
          .load()
          .getConfig("ctx")
          .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue()))
      )
    )

  describe("An Catalog Item") {
    describe("after being added") {
      it("should be present in database") {
        val db: Repository = repository.getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(1).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val inPlaceCatalogItem: InPlaceCatalogItem = db.add(category, store, price).getOrElse(fail())
        db.findById(inPlaceCatalogItem.id, store).value shouldBe inPlaceCatalogItem
        db.remove(inPlaceCatalogItem).getOrElse(fail())
      }
    }

    describe("if never added") {
      it("should not be present") {
        val db: Repository = repository.getOrElse(fail())
        val id: CatalogItemId = CatalogItemId(1).getOrElse(fail())
        val store: Store = Store(1).getOrElse(fail())
        db.findById(id, store).left.value shouldBe CatalogItemNotFound
      }
    }

    describe("after being added and then deleted") {
      it("should not be present in database") {
        val db: Repository = repository.getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(2).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val inPlaceCatalogItem: InPlaceCatalogItem = db.add(category, store, price).getOrElse(fail())
        db.remove(inPlaceCatalogItem).getOrElse(fail())
        db.findById(inPlaceCatalogItem.id, store).left.value shouldBe CatalogItemNotFound
      }
    }

    describe("after being removed but it were never added in the first place") {
      it("should not be allowed") {
        val db: Repository = repository.getOrElse(fail())
        val id: CatalogItemId = CatalogItemId(2).getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(2).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val inPlaceCatalogItem: InPlaceCatalogItem = InPlaceCatalogItem(id, category, store, price)
        db.remove(inPlaceCatalogItem).left.value shouldBe OperationFailed
      }
    }

    describe("after being added and then it data gets updated") {
      it("should show the update") {
        val db: Repository = repository.getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(3).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: InPlaceCatalogItem = db.add(category, store, price).getOrElse(fail())
        val newPrice = Price(Amount(14.99).getOrElse(fail()), Currency.withName("USD"))
        db.update(catalogItem, newPrice).getOrElse(fail())
        db.findById(catalogItem.id, store).value.price shouldBe newPrice
        db.remove(catalogItem).getOrElse(fail())
      }
    }

    describe("when it data gets updated but they were never added in the first place") {
      it("should not be allowed") {
        val db: Repository = repository.getOrElse(fail())
        val id: CatalogItemId = CatalogItemId(3).getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(3).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: InPlaceCatalogItem = InPlaceCatalogItem(id, category, store, price)
        db.update(catalogItem, price).left.value shouldBe OperationFailed
      }
    }

    describe("if never registered, while searching for all lifted catalog items") {
      it("should not be in the database") {
        val db: Repository = repository.getOrElse(fail())
        val empty: Int = 0
        db.findAllLifted().value.size shouldBe empty
      }
    }

    describe("if added twice, while searching for all lifted catalog items") {
      it("should be in the database along with its clone") {
        val db: Repository = repository.getOrElse(fail())
        val empty: Int = 0
        val size: Int = 2
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(4).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val inPlaceCatalogItemA: InPlaceCatalogItem = db.add(category, store, price).getOrElse(fail())
        db.update(inPlaceCatalogItemA.lift, price).getOrElse(fail())
        val inPlaceCatalogItemB: InPlaceCatalogItem = db.add(category, store, price).getOrElse(fail())
        db.update(inPlaceCatalogItemB.lift, price).getOrElse(fail())
        db.findAllLifted().value.size shouldBe size
        db.remove(inPlaceCatalogItemA.lift).getOrElse(fail())
        db.remove(inPlaceCatalogItemB.lift).getOrElse(fail())
        db.findAllLifted().value.size shouldBe empty
      }
    }
  }
}
