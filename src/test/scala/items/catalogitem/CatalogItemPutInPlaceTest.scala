package io.github.pervasivecats
package items.catalogitem

import io.github.pervasivecats.items.catalogitem.domainevents.CatalogItemPutInPlace
import io.github.pervasivecats.items.catalogitem.valueobjects.{CatalogItemId, Store}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

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
