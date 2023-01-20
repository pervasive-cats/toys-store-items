/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory.entities

import items.itemcategory.valueobjects.*

trait ItemCategoryOps[A <: ItemCategory] {

  def updated(name: Name, description: Description): A
}
