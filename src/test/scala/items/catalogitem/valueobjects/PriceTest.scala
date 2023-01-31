package io.github.pervasivecats
package items.catalogitem.valueobjects

import items.catalogitem.valueobjects.{Amount, Currency, Price}

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.shouldBe

class PriceTest extends AnyFunSpec {

  describe("A Price") {
    describe("when created with an amount and a price") {
      it("should be contain them") {
        val amount: Amount = Amount(10).getOrElse(fail())
        val currency: Currency = Currency.withName("EUR")
        val price: Price = Price(amount, currency)
        price.amount shouldBe amount
        price.currency shouldBe currency
      }
    }
  }
}
