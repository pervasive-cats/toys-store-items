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

  describe("An Item") {
    describe("when s") {
      it("should ") {
        val db = repository.getOrElse(fail())

        val itemId = ItemId(1).getOrElse(fail())
        val catalogItemId = CatalogItemId(345).getOrElse(fail())
        val store: Store = Store(614).getOrElse(fail())
        db.findById(itemId, catalogItemId, store).getOrElse(fail())
      }
    }
  }
}
