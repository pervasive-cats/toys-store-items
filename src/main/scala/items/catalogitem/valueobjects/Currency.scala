/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.valueobjects

import enumeratum._

sealed trait Currency extends EnumEntry

object Currency extends Enum[Currency] {

  val values: IndexedSeq[Currency] = findValues

  case object EUR extends Currency
  
  case object USD extends Currency
  
  case object GBP extends Currency
  
  case object CHF extends Currency
}
