/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.NonNegative

type IdNumber = Long Refined NonNegative

type Validated[A] = Either[ValidationError, A]

trait ValidationError {

  val message: String
}
