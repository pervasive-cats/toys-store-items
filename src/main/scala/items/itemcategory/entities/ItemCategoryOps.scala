/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory.entities

import io.github.pervasivecats.items.Id

import items.itemcategory.valueobjects.*

trait ItemCategoryOps[A <: ItemCategory] {

  def updated(itemCategory: ItemCategory, name: Name, description: Description): A
}

object ItemCategoryOps {

  extension [A <: ItemCategory: ItemCategoryOps](item: A) {

    @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments", "scalafix:DisableSyntax.defaultArgs"))
    def updated(
      itemCategory: ItemCategory = item,
      name: Name = item.name,
      description: Description = item.description
    ): A =
      implicitly[ItemCategoryOps[A]].updated(itemCategory, name, description)
  }
}
