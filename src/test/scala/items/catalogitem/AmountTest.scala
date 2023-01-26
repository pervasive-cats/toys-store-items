package io.github.pervasivecats
package items.catalogitem

import scala.language.postfixOps

import io.github.pervasivecats.items.catalogitem.valueobjects.Amount
import io.github.pervasivecats.items.catalogitem.valueobjects.Amount.WrongAmountFormat

import eu.timepit.refined.auto.autoUnwrap
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

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
        Amount(negativeAmount) shouldBe WrongAmountFormat
      }
    }

    describe("when created with a value of 0") {
      it("should be valid") {
        (Amount(zeroAmount).getOrElse(fail()).value: Double) shouldBe zeroAmount
      }
    }
  }
}
