package io.github.pervasivecats
package items.itemcategory

import java.nio.file.attribute.UserPrincipalNotFoundException

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

import io.github.pervasivecats.items.ValidationError
import io.github.pervasivecats.items.itemcategory.Repository.ItemCategoryNotFound
import io.github.pervasivecats.items.itemcategory.Repository.OperationFailed
import io.github.pervasivecats.items.itemcategory.entities.ItemCategory
import io.github.pervasivecats.items.itemcategory.valueobjects.Description
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId
import io.github.pervasivecats.items.itemcategory.valueobjects.Name

import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

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
        val db: Repository = repository.getOrElse(fail())
        val id: ItemCategoryId = ItemCategoryId(1).getOrElse(fail())
        val name: Name = Name("Terraforming Mars").getOrElse(fail())
        val description: Description = Description("Boardgame produced by BraditGamesStudio").getOrElse(fail())
        val itemCategory: ItemCategory = ItemCategory(id, name, description)
        db.add(name, description).getOrElse(fail())
        db.findById(id).getOrElse(fail()).id shouldBe id
        db.remove(itemCategory).getOrElse(fail())
      }
    }

    describe("if never registered") {
      it("should not be present") {
        val db: Repository = repository.getOrElse(fail())
        val id: ItemCategoryId = ItemCategoryId(9000).getOrElse(fail())
        db.findById(id).left.value shouldBe ItemCategoryNotFound
      }
    }

    describe("after being registered and then deleted") {
      it("should not be present in database") {
        val db: Repository = repository.getOrElse(fail())
        val id: ItemCategoryId = ItemCategoryId(2).getOrElse(fail())
        val name: Name = Name("Terraforming Mars").getOrElse(fail())
        val description: Description = Description("Boardgame produced by BraditGamesStudio").getOrElse(fail())
        val itemCategory: ItemCategory = ItemCategory(id, name, description)
        db.add(name, description).getOrElse(fail())
        db.remove(itemCategory).getOrElse(fail())
        db.findById(id).left.value shouldBe ItemCategoryNotFound
      }
    }

    describe("after being registered and then their data gets updated") {
      it("should show the update") {
        val db: Repository = repository.getOrElse(fail())
        val id: ItemCategoryId = ItemCategoryId(3).getOrElse(fail())
        val name: Name = Name("Terraforming Mars").getOrElse(fail())
        val description: Description = Description("Boardgame produced by BraditGamesStudio").getOrElse(fail())
        db.add(name, description).getOrElse(fail())
        val updatedName: Name = Name("7 Wonders").getOrElse(fail())
        val updatedDescription: Description = Description("Boardgame produced by REPOS production").getOrElse(fail())
        val updatedItemCategory: ItemCategory = ItemCategory(id, updatedName, updatedDescription)
        db.update(updatedItemCategory, updatedName, updatedDescription)
        db.findById(id).getOrElse(fail()).name shouldBe updatedName
        db.findById(id).getOrElse(fail()).description shouldBe updatedDescription
      }
    }

    describe("when their data gets updated but they were never registered in the first place") {
      it("should not be allowed") {
        val db: Repository = repository.getOrElse(fail())
        val id: ItemCategoryId = ItemCategoryId(9000).getOrElse(fail())
        val name: Name = Name("Throw Throw Burrito").getOrElse(fail())
        val description: Description = Description("What you get when you cross a card game with dodgeball").getOrElse(fail())
        val itemCategory: ItemCategory = ItemCategory(id, name, description)
        db.update(itemCategory, name, description).left.value shouldBe OperationFailed
      }
    }
  }
}
