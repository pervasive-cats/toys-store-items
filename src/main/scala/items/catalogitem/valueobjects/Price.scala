/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.valueobjects

import io.github.pervasivecats.ValidationError

trait Price {

  val amount: Amount

  val currency: Currency
}

object Price {

  final private case class PriceImpl(amount: Amount, currency: Currency) extends Price

  def apply(amount: Amount, currency: Currency): Price = PriceImpl(amount, currency)
}
