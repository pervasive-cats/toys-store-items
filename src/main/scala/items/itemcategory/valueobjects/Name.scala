/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory.valueobjects

import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.MatchesRegex

type NameString = String Refined MatchesRegex["^[a-zA-Z\\W]{1,99}$"]

trait Name {

  val name: NameString
}
