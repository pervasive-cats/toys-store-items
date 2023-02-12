/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory.valueobjects

import scala.language.postfixOps

import eu.timepit.refined.auto.autoUnwrap
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

import items.ValidationError
import items.itemcategory.valueobjects.Name
import items.itemcategory.valueobjects.Name.WrongNameFormat

class NameTest extends AnyFunSpec {

  describe("A Name") {
    describe("when created with an initial space") {
      it("should not be valid") {
        Name(" Lego Bat Mobile") shouldBe Left[ValidationError, Name](WrongNameFormat)
      }
    }

    describe("when created with a end space") {
      it("should not be valid") {
        Name("Lego Bat Mobile ") shouldBe Left[ValidationError, Name](WrongNameFormat)
      }
    }

    describe("when created with empty string") {
      it("should not be valid") {
        Name("") shouldBe Left[ValidationError, Name](WrongNameFormat)
      }
    }

    describe("when created with number and characters") {
      it("should be valid") {
        (Name("7 Wonders").getOrElse(fail()).value: String) shouldBe "7 Wonders"
      }
    }
  }
}
