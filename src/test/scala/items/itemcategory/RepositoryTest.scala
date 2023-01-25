package io.github.pervasivecats
package items.itemcategory

import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import io.github.pervasivecats.items.ValidationError
import io.github.pervasivecats.items.itemcategory.Repository.{ItemCategoryNotFound, OperationFailed}
import io.github.pervasivecats.items.itemcategory.entities.ItemCategory
import io.github.pervasivecats.items.itemcategory.valueobjects.{Description, ItemCategoryId, Name}
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import java.nio.file.attribute.UserPrincipalNotFoundException
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

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

  describe("An Item Category") {
    describe("after being registered") {
      it("should be present in database") {
        val name: Name = Name("Lego Bat Mobile").getOrElse(fail())
        val description: Description = Description("Random Description").getOrElse(fail())
        repository.getOrElse(fail()).add(name,description).getOrElse(fail())
        val id: ItemCategoryId = ItemCategoryId(1).getOrElse(fail())
        repository.getOrElse(fail()).findById(id).getOrElse(fail()).id shouldBe id
      }
    }

    describe("when asked to retrieve the item category corresponding to a non existent id") {
      it("should return item category not found") {
        val id: ItemCategoryId = ItemCategoryId(9000).getOrElse(fail())
        repository.getOrElse(fail()).findById(id).left.value shouldBe ItemCategoryNotFound
      }
    }
  }
}
