package io.github.pervasivecats
package items.itemcategory

import scala.language.postfixOps

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

import items.itemcategory.entities.ItemCategory
import items.itemcategory.entities.ItemCategoryOps.updated
import items.itemcategory.valueobjects.{Description, ItemCategoryId, Name}

class ItemCategoryTest extends AnyFunSpec {

  val itemCategoryId: ItemCategoryId = ItemCategoryId(9000).getOrElse(fail())
  val name: Name = Name("Lego Bat Mobile").getOrElse(fail())

  val description: Description = Description(
    "The model includes 2 light bricks – 1 red and 1 yellow. The red light " +
    "adds an awesome glow to the transparent toy engine at the back, while the yellow brick lights up the front grille. " +
    "Other cool features include steering on the front wheels, differential on the rear wheels, a spinning "
  ).getOrElse(fail())
  val itemCategory: ItemCategory = ItemCategory(itemCategoryId, name, description)

  describe("An ItemCategory") {
    describe("when created with a item category id, name and description") {
      it("should contain them") {
        itemCategory.id shouldBe itemCategoryId
        itemCategory.name shouldBe name
        itemCategory.description shouldBe description
      }
    }

    describe("when updated with a new name") {
      it("should contain it") {
        val newName: Name = Name("Lego Bat Mobile New Edition").getOrElse(fail())
        itemCategory.updated(name = newName).name shouldBe newName
      }
    }

    describe("when updated with a new description") {
      it("should contain it") {
        val newDescription: Description = Description("New Description").getOrElse(fail())
        itemCategory.updated(description = newDescription).description shouldBe newDescription
      }
    }
  }
}
