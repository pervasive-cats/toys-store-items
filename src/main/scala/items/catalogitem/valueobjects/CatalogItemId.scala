/*
* Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
*
* All Rights Reserved.
*/

package io.github.pervasivecats
package items.catalogitem.valueobjects

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

type CatalogItemIdLong = Long Refined NonNegative

trait CatalogItemId {

  val value: CatalogItemIdLong
}
