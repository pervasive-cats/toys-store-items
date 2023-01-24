package io.github.pervasivecats
package items.itemcategory

import scala.language.postfixOps

import io.github.pervasivecats.items.ValidationError
import io.github.pervasivecats.items.itemcategory.valueobjects.Name
import io.github.pervasivecats.items.itemcategory.valueobjects.Name.WrongNameFormat

import eu.timepit.refined.auto.autoUnwrap
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

class NameTest extends AnyFunSpec {

  describe("A Name") {
    describe("when created with an initial space") {
      it("should not be valid") {
        val name: String = " Lego Bat Mobile"
        Name(name) shouldBe Left[ValidationError, Name](WrongNameFormat)
      }
    }

    describe("when created with a end space") {
      it("should not be valid") {
        val name = "Lego Bat Mobile "
        Name(name) shouldBe Left[ValidationError, Name](WrongNameFormat)
      }
    }

    describe("when created with empty string") {
      it("should not be valid") {
        val name: String = ""
        Name(name) shouldBe Left[ValidationError, Name](WrongNameFormat)
      }
    }

    describe("when created with number and characters") {
      it("should be valid") {
        val name: String = "7 Wonders"
        (Name(name).getOrElse(fail()).name: String) shouldBe name
      }
    }

    describe("when contain a space") {
      it("should be valid") {
        val name: String = "Lego Bat Mobile"
        (Name(name).getOrElse(fail()).name: String) shouldBe name
      }
    }
  }
}
