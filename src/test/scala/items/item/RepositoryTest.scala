package io.github.pervasivecats
package items.item

import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import org.scalatest.funspec.AnyFunSpec
import org.testcontainers.utility.DockerImageName
import io.github.pervasivecats.items.item.valueobjects.ItemId
import io.github.pervasivecats.items.catalogitem.valueobjects.CatalogItemId
import io.github.pervasivecats.items.catalogitem.valueobjects.Store
import io.github.pervasivecats.items.catalogitem.entities.CatalogItem
import items.catalogitem.valueobjects.*
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId
import io.github.pervasivecats.items.item.valueobjects.Customer
import items.item.entities.*
import org.scalatest.matchers.should.Matchers.*

import scala.concurrent.duration.{FiniteDuration, SECONDS}

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

  describe("The Item Repository") {
    describe("when asked to add an Item") {
      it("should add the entry in the database") {
        val db = repository.getOrElse(fail())

        val catalogItemId: CatalogItemId = CatalogItemId(345).getOrElse(fail())
        val category: ItemCategoryId = ItemCategoryId(614).getOrElse(fail())
        val store: Store = Store(15).getOrElse(fail())
        val price: Price = Price(Amount(19.99).getOrElse(fail()), Currency.withName("EUR"))
        val inPlaceCatalogItem: InPlaceCatalogItem = InPlaceCatalogItem(catalogItemId, category, store, price)
        val customer: Customer = Customer("elena@gmail.com").getOrElse(fail())
        val inCartItem: Item = db.add(catalogItemId, customer, store).getOrElse(fail())

        inCartItem.id shouldBe ItemId(0).getOrElse(fail())
        inCartItem.kind shouldBe inPlaceCatalogItem
      }
    }
  }
}
