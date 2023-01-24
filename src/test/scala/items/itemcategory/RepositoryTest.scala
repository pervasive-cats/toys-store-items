package io.github.pervasivecats
package items.itemcategory

import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import java.nio.file.attribute.UserPrincipalNotFoundException
import scala.concurrent.duration.{DurationInt, FiniteDuration}

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
    repository = Some(Repository.withPort(containers.container.getFirstMappedPort.intValue()))

  describe("A Repository") {
    describe("when asked to retrieve the administration account corresponding to a id") {
      it("should return the corresponding item category") {
        val id: ItemCategoryId = ItemCategoryId(10103442).getOrElse(fail())
        repository.getOrElse(fail()).findById(id).getOrElse(fail()).id shouldBe id
      }
    }
  }
}
