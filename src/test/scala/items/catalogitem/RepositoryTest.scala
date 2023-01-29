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

  private val id: CatalogItemId = CatalogItemId(1).getOrElse(fail())
  private val category: ItemCategoryId = ItemCategoryId(35).getOrElse(fail())
  private val store: Store = Store(1).getOrElse(fail())
  private val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
  val catalogItem: CatalogItem = CatalogItem(id, category, store, price)

  describe("A Item Category") {
    describe("after being registrated") {
      it("should be present in database") {
        val db: Repository = repository.getOrElse(fail())
        db.add(catalogItem).getOrElse(fail())
        db.findById(id, store).getOrElse(fail()) shouldBe catalogItem
      }
    }
  }
}
