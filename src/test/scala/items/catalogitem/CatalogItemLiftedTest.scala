package io.github.pervasivecats
package items.catalogitem

import io.github.pervasivecats.items.catalogitem.domainevents.CatalogItemLifted
import io.github.pervasivecats.items.catalogitem.valueobjects.CatalogItemId
import io.github.pervasivecats.items.catalogitem.valueobjects.Store

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

class CatalogItemLiftedTest extends AnyFunSpec {

  describe("A catalog item lifted event") {
    describe("when created with a catalog item id and a store") {
      it("should contain them") {
        val catalogItemId: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
        val store: Store = Store(39).getOrElse(fail())
        CatalogItemLifted(catalogItemId, store).catalogItemId shouldBe catalogItemId
        CatalogItemLifted(catalogItemId, store).store shouldBe store
      }
    }
  }
}
