/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.valueobjects

import scala.language.postfixOps

import eu.timepit.refined.auto.autoUnwrap
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

import items.ValidationError
import items.catalogitem.valueobjects.Amount
import items.catalogitem.valueobjects.Amount.WrongAmountFormat

class AmountTest extends AnyFunSpec {

  val positiveAmount: Double = 24.95
  val negativeAmount: Double = -19.99
  val zeroAmount: Double = 0.00

  describe("An Amount") {
    describe("When created with a positive value") {
      it("should be valid") {
        (Amount(positiveAmount).getOrElse(fail()).value: Double) shouldBe positiveAmount
      }
    }

    describe("when created with a negative value") {
      it("should not be valid") {
        Amount(negativeAmount) shouldBe Left[ValidationError, Amount](WrongAmountFormat)
      }
    }

    describe("when created with a value of 0") {
      it("should not be valid") {
        Amount(zeroAmount) shouldBe Left[ValidationError, Amount](WrongAmountFormat)
      }
    }
  }
}
