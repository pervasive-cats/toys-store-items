package io.github.pervasivecats
package items.itemcategory

import io.github.pervasivecats.items.{Id, ValidationError}
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*

import scala.language.postfixOps
import eu.timepit.refined.auto.autoUnwrap
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId.WrongStoreIdFormat

class ItemCategoryIdTest extends AnyFunSpec{

  val value: Long = 9000
  val negativeValue: Long = -9000
  val zeroValue: Long = 0

  describe("An ItemCategoryId") {
    describe("when created with a positive value") {
      it("should be valid") {
        (ItemCategoryId(value).getOrElse(fail()).value : Long) shouldBe value
      }
    }

    describe("when created with value 0") {
      it("should be valid") {
        (ItemCategoryId(zeroValue).getOrElse(fail()).value : Long) shouldBe zeroValue
      }
    }

    describe("when created with a negative value") {
      it("should not be valid") {
        ItemCategoryId(negativeValue) shouldBe Left[ValidationError, ItemCategoryId](WrongStoreIdFormat)
      }
    }
  }
}
