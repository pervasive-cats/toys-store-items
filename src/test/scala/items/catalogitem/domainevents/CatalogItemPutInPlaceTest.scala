/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.domainevents

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

import items.catalogitem.domainevents.CatalogItemPutInPlace
import items.catalogitem.valueobjects.{CatalogItemId, Store}

class CatalogItemPutInPlaceTest extends AnyFunSpec {

  describe("A catalog item put in place event") {
    describe("when created with a catalog item id and a store") {
      it("should contain them") {
        val catalogItemId: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
        val store: Store = Store(39).getOrElse(fail())
        CatalogItemPutInPlace(catalogItemId, store).catalogItemId shouldBe catalogItemId
        CatalogItemPutInPlace(catalogItemId, store).store shouldBe store
      }
    }
  }
}
