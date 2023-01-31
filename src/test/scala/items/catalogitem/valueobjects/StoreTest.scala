package io.github.pervasivecats
package items.catalogitem.valueobjects

import items.ValidationError
import items.catalogitem.valueobjects.Store
import items.catalogitem.valueobjects.Store.WrongStoreFormat

import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

class StoreTest extends AnyFunSpec {

  val negativeValue: Long = -9000
  val positiveValue: Long = 9000
  val zeroValue: Long = 0

  describe("A store") {
    describe("when created with a negative value identifier") {
      it("should not be valid") {
        Store(negativeValue) shouldBe Left[ValidationError, Store](WrongStoreFormat)
      }
    }

    describe("when created with a positive value identifier") {
      it("should be valid") {
        (Store(positiveValue).getOrElse(fail()).id: Long) shouldBe positiveValue
      }
    }

    describe("when created with an identifier of value 0") {
      it("should be valid") {
        (Store(zeroValue).value.id: Long) shouldBe zeroValue
      }
    }
  }
}
