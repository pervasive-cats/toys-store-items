/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes.entities

import io.github.pervasivecats.items.item.valueobjects.ItemId

import spray.json.DefaultJsonProtocol.jsonFormat3
import spray.json.RootJsonFormat

import application.routes.entities.Entity
import application.Serializers.given
import items.catalogitem.valueobjects.*
import items.itemcategory.valueobjects.ItemCategoryId

sealed trait ItemEntity extends Entity

object ItemEntity {

  final case class ItemShowEntity(id: ItemId, kind: CatalogItemId, store: Store) extends ItemEntity

  given RootJsonFormat[ItemShowEntity] = jsonFormat3(ItemShowEntity.apply)

  final case class ItemAdditionEntity(id: ItemId, kind: CatalogItemId, store: Store) extends ItemEntity

  given RootJsonFormat[ItemAdditionEntity] = jsonFormat3(ItemAdditionEntity.apply)

  final case class ItemRemovalEntity(id: ItemId, kind: CatalogItemId, store: Store) extends ItemEntity

  given RootJsonFormat[ItemRemovalEntity] = jsonFormat3(ItemRemovalEntity.apply)
}
