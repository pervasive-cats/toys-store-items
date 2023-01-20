/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory

import items.itemcategory.valueobjects.*
import items.Validated
import items.itemcategory.entities.ItemCategory

trait Repository {

  def findById(id: ItemCategoryId): Validated[ItemCategory]

  def add(name: Name, description: Description): Validated[Unit]

  def update(itemCategory: ItemCategory, name: Name, description: Description): Validated[Unit]

  def remove(itemCategory: ItemCategory): Validated[Unit]

}