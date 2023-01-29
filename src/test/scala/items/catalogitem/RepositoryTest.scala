package io.github.pervasivecats
package items.catalogitem

import scala.concurrent.duration.FiniteDuration
import io.github.pervasivecats.items.catalogitem.entities.CatalogItem
import io.github.pervasivecats.items.catalogitem.valueobjects.Amount
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
import org.scalatest.matchers.should.Matchers.*
import scala.language.postfixOps
import org.scalatest.EitherValues.given
import io.github.pervasivecats.items.catalogitem.Repository.{CatalogItemNotFound, OperationFailed}
import org.scalatest.funspec.AnyFunSpec
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
        val id: CatalogItemId = CatalogItemId(1).getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(1).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: CatalogItem = CatalogItem(id, category, store, price)
        db.add(catalogItem).getOrElse(fail())
        db.findById(id, store).getOrElse(fail()) shouldBe catalogItem
        db.remove(catalogItem).getOrElse(fail())
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
        val id: CatalogItemId = CatalogItemId(2).getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(2).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: CatalogItem = CatalogItem(id, category, store, price)
        db.add(catalogItem).getOrElse(fail())
        db.remove(catalogItem).getOrElse(fail())
        db.findById(id, store).left.value shouldBe CatalogItemNotFound
      }
    }

    describe("after being removed but they were never registered in the first place") {
      it("should not be allowed") {
        val db: Repository = repository.getOrElse(fail())
        val id: CatalogItemId = CatalogItemId(2).getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(2).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: CatalogItem = CatalogItem(id, category, store, price)
        db.remove(catalogItem).left.value shouldBe OperationFailed
      }
    }

    describe("after being registered and then their data gets updated") {
      it("should show the update") {
        val db: Repository = repository.getOrElse(fail())
        val id: CatalogItemId = CatalogItemId(3).getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
        val store: Store = Store(3).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val catalogItem: CatalogItem = CatalogItem(id, category, store, price)
        db.add(catalogItem).getOrElse(fail())
        val newPrice = Price(Amount(14.99).getOrElse(fail()), Currency.withName("USD"))
        db.update(catalogItem, newPrice).getOrElse(fail())
        db.findById(id, store).getOrElse(fail()).price shouldBe newPrice
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
        val catalogItem: CatalogItem = CatalogItem(id, category, store, price)
        db.update(catalogItem, price).left.value shouldBe OperationFailed
      }
    }

  }
}
