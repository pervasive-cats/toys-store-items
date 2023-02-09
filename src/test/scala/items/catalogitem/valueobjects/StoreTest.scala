/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.valueobjects

import io.github.pervasivecats.ValidationError

import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

import items.catalogitem.valueobjects.Store
import items.catalogitem.valueobjects.Store.WrongStoreFormat

class StoreTest extends AnyFunSpec {

  private val positiveValue: Long = 9000
  private val zeroValue: Long = 0

  describe("A store") {
    describe("when created with a negative value identifier") {
      it("should not be valid") {
        Store(-9000).left.value shouldBe WrongStoreFormat
      }
    }

    describe("when created with a positive value identifier") {
      it("should be valid") {
        (Store(positiveValue).value.id: Long) shouldBe positiveValue
      }
    }

    describe("when created with an identifier of value 0") {
      it("should be valid") {
        (Store(zeroValue).value.id: Long) shouldBe zeroValue
      }
    }
  }
}
