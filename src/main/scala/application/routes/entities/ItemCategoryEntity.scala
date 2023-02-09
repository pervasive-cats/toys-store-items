/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes.entities

import spray.json.DefaultJsonProtocol.jsonFormat1
import spray.json.DefaultJsonProtocol.jsonFormat2
import spray.json.DefaultJsonProtocol.jsonFormat3
import spray.json.RootJsonFormat

import application.routes.entities.Entity
import items.itemcategory.valueobjects.{Description, ItemCategoryId, Name}
import application.Serializers.given

sealed trait ItemCategoryEntity extends Entity

object ItemCategoryEntity {

  final case class ItemCategoryShowEntity(id: ItemCategoryId) extends ItemCategoryEntity

  given RootJsonFormat[ItemCategoryShowEntity] = jsonFormat1(ItemCategoryShowEntity.apply)

  final case class ItemCategoryAdditionEntity(name: Name, description: Description) extends ItemCategoryEntity

  given RootJsonFormat[ItemCategoryAdditionEntity] = jsonFormat2(ItemCategoryAdditionEntity.apply)

  final case class ItemCategoryUpdateEntity(id: ItemCategoryId, newName: Name, newDescription: Description)
    extends ItemCategoryEntity

  given RootJsonFormat[ItemCategoryUpdateEntity] = jsonFormat3(ItemCategoryUpdateEntity.apply)

  final case class ItemCategoryRemovalEntity(id: ItemCategoryId) extends ItemCategoryEntity

  given RootJsonFormat[ItemCategoryRemovalEntity] = jsonFormat1(ItemCategoryRemovalEntity.apply)
}
