/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.valueobjects

import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe
import items.catalogitem.valueobjects.Count
import items.catalogitem.valueobjects.Count.WrongCountFormat
import io.github.pervasivecats.ValidationError

class CountTest extends AnyFunSpec {

  private val positiveValue: Long = 9000

  describe("A count value") {
    describe("when created with a negative value") {
      it("should not be valid") {
        Count(-9000).left.value shouldBe WrongCountFormat
      }
    }

    describe("when created with a positive value") {
      it("should be valid") {
        (Count(positiveValue).value.value: Long) shouldBe positiveValue
      }
    }

    describe("when created with the 0 value") {
      it("should be valid") {
        Count(0L).left.value shouldBe WrongCountFormat
      }
    }
  }
}
