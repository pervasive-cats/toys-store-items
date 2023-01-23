/*
* Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
*
* All Rights Reserved.
*/

package io.github.pervasivecats
package items.catalogitem.valueobjects

trait Price {

  val amount: Amount
  val currency: Currency
}