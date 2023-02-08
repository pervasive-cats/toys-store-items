/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory.valueobjects



import eu.timepit.refined.auto.autoUnwrap
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe
import items.itemcategory.valueobjects.Description
import items.itemcategory.valueobjects.Description.WrongDescriptionFormat
import io.github.pervasivecats.ValidationError

class DescriptionTest extends AnyFunSpec {

  describe("A Description") {
    describe("when created with empty string") {
      it("should not be valid") {
        Description("") shouldBe Left[ValidationError, Description](WrongDescriptionFormat)
      }
    }
  }

  describe("when created with a non-empty string") {
    it("should be valid") {
      val description: String =
        """
          |7 Wonders is a board game created by Antoine Bauza in 2010 and originally published by Repos Production
          |(part of Asmodee Group). 7 Wonders is a card drafting game that is played using three decks of cards
          |featuring depictions of ancient civilizations, military conflicts, and commercial activity.
          |""".stripMargin
      (Description(description).getOrElse(fail()).value: String) shouldBe description
    }
  }
}
