/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory.entities

import items.Id
import items.itemcategory.valueobjects.*

trait ItemCategoryOps[A <: ItemCategory] {

  def updated(itemCategory: ItemCategory, name: Name, description: Description): A
}

object ItemCategoryOps {

  extension [A <: ItemCategory: ItemCategoryOps](itemCategory: A) {

    @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments", "scalafix:DisableSyntax.defaultArgs"))
    def updated(
      name: Name = itemCategory.name,
      description: Description = itemCategory.description
    ): A =
      implicitly[ItemCategoryOps[A]].updated(itemCategory, name, description)
  }
}
