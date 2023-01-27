/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory.entities

import items.itemcategory.valueobjects.*

trait ItemCategory {

  val id: ItemCategoryId

  val name: Name

  val description: Description
}

object ItemCategory {

  private case class ItemCategoryImpl(id: ItemCategoryId, name: Name, description: Description) extends ItemCategory

  given ItemCategoryOps[ItemCategory] with {

    override def updated(itemCategory: ItemCategory, name: Name, description: Description): ItemCategory =
      ItemCategoryImpl(itemCategory.id, name, description)
  }

  def apply(id: ItemCategoryId, name: Name, description: Description): ItemCategory = ItemCategoryImpl(id, name, description)
}
