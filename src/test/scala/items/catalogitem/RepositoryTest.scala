package io.github.pervasivecats
package items.catalogitem

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import io.github.pervasivecats.items.catalogitem.Repository.CatalogItemNotFound
import io.github.pervasivecats.items.catalogitem.Repository.OperationFailed
import io.github.pervasivecats.items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import io.github.pervasivecats.items.catalogitem.valueobjects.Amount
import io.github.pervasivecats.items.catalogitem.entities.InPlaceCatalogItemOps.lift
import io.github.pervasivecats.items.catalogitem.valueobjects.CatalogItemId
import io.github.pervasivecats.items.catalogitem.valueobjects.Currency
import io.github.pervasivecats.items.catalogitem.valueobjects.Price
import io.github.pervasivecats.items.catalogitem.valueobjects.Store
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId
import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.getquill.autoQuote
import io.github.pervasivecats.items.Validated
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.scalatest.matchers.should.Matchers.shouldBe
import org.testcontainers.utility.DockerImageName

import concurrent.duration.DurationInt

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

  describe("An Item Category") {
    describe("after being registrated") {
      it("should be present in database") {
        val db: Repository = repository.getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(1).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val inPlaceCatalogItem: InPlaceCatalogItem = db.add(category, store, price).getOrElse(fail())
        db.findById(inPlaceCatalogItem.id, store).getOrElse(fail()) shouldBe inPlaceCatalogItem
        db.remove(inPlaceCatalogItem).getOrElse(fail())
      }
    }

    describe("if never registered") {
      it("should not be present") {
        val db: Repository = repository.getOrElse(fail())
        val id: CatalogItemId = CatalogItemId(1).getOrElse(fail())
        val store: Store = Store(1).getOrElse(fail())
        db.findById(id, store).left.value shouldBe CatalogItemNotFound
      }
    }

    describe("after being registered and then deleted") {
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

    describe("after being removed but they were never registered in the first place") {
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

    describe("after being registered and then their data gets updated") {
      it("should show the update") {
        val db: Repository = repository.getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(3).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: InPlaceCatalogItem = db.add(category, store, price).getOrElse(fail())
        val newPrice = Price(Amount(14.99).getOrElse(fail()), Currency.withName("USD"))
        db.update(catalogItem, newPrice).getOrElse(fail())
        db.findById(catalogItem.id, store).getOrElse(fail()).price shouldBe newPrice
        db.remove(catalogItem).getOrElse(fail())
      }
    }

    describe("when their data gets updated but they were never registered in the first place") {
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

    describe("after ") {
      it("should be registered") {
        val db: Repository = repository.getOrElse(fail())
        val empty: Int = 0
        db.findAllLifted().getOrElse(fail()).size shouldBe empty
      }
    }

    describe("after") {
      it("should be") {
        val db: Repository = repository.getOrElse(fail())
        val empty: Int = 0
        val size: Int = 2
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(4).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val inPlaceCatalogItemA: InPlaceCatalogItem = db.add(category, store, price).getOrElse(fail())
        db.update(inPlaceCatalogItemA.lift, price)
        val inPlaceCatalogItemB: InPlaceCatalogItem = db.add(category, store, price).getOrElse(fail())
        db.update(inPlaceCatalogItemB.lift, price)
        db.findAllLifted().getOrElse(fail()).size shouldBe size
        db.remove(inPlaceCatalogItemA.lift)
        db.remove(inPlaceCatalogItemB.lift)
        db.findAllLifted().getOrElse(fail()).size shouldBe empty
      }
    }
  }
}
