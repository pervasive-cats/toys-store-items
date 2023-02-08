/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.valueobjects

import eu.timepit.refined.auto.autoUnwrap
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe
import items.catalogitem.valueobjects.CatalogItemId
import items.catalogitem.valueobjects.CatalogItemId.WrongCatalogItemIdFormat
import items.itemcategory.valueobjects.ItemCategoryId
import io.github.pervasivecats.ValidationError

class CatalogItemIdTest extends AnyFunSpec {

  val value: Long = 9000
  val negativeValue: Long = -9000
  val zeroValue: Long = 0

  describe("A Catalog Item Id") {
    describe("when created with a positive value") {
      it("should be valid") {
        (CatalogItemId(value).getOrElse(fail()).value: Long) shouldBe value
      }
    }

    describe("when created with value 0") {
      it("should be valid") {
        (CatalogItemId(zeroValue).getOrElse(fail()).value: Long) shouldBe zeroValue
      }
    }

    describe("when created with a negative value") {
      it("should not be valid") {
        CatalogItemId(negativeValue) shouldBe Left[ValidationError, CatalogItemId](WrongCatalogItemIdFormat)
      }
    }
  }
}
