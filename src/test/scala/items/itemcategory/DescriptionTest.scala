package io.github.pervasivecats
package items.itemcategory

import io.github.pervasivecats.items.ValidationError
import io.github.pervasivecats.items.itemcategory.valueobjects.Description
import io.github.pervasivecats.items.itemcategory.valueobjects.Description.WrongDescriptionFormat
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe
import eu.timepit.refined.auto.autoUnwrap

import scala.language.postfixOps

class DescriptionTest extends AnyFunSpec{

  describe("A Description") {
    describe("when created with empty string") {
      it("should not be valid") {
        val description: String = ""
        Description(description) shouldBe Left[ValidationError, Description](WrongDescriptionFormat)
      }
    }
  }

  describe("when created with a non-empty string"){
    it("should be valid") {
      val description: String = "7 Wonders is a board game created by Antoine Bauza in 2010 and originally published " +
        "by Repos Production (part of Asmodee Group). 7 Wonders is a card drafting game that is played using three" +
        " decks of cards featuring depictions of ancient civilizations, military conflicts, and commercial activity."
      (Description(description).getOrElse(fail()).description : String) shouldBe description
    }
  }
}