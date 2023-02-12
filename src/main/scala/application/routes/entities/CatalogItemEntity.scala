/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes.entities

import spray.json.DefaultJsonProtocol.jsonFormat2
import spray.json.DefaultJsonProtocol.jsonFormat3
import spray.json.RootJsonFormat

import application.routes.entities.Entity
import application.Serializers.given
import items.catalogitem.valueobjects.*
import items.itemcategory.valueobjects.ItemCategoryId

sealed trait CatalogItemEntity extends Entity

object CatalogItemEntity {

  final case class CatalogItemShowEntity(id: CatalogItemId, store: Store) extends CatalogItemEntity

  given RootJsonFormat[CatalogItemShowEntity] = jsonFormat2(CatalogItemShowEntity.apply)

  final case class CatalogItemAdditionEntity(itemCategoryId: ItemCategoryId, store: Store, price: Price) extends CatalogItemEntity

  given RootJsonFormat[CatalogItemAdditionEntity] = jsonFormat3(CatalogItemAdditionEntity.apply)

  final case class CatalogItemUpdateEntity(id: CatalogItemId, store: Store, price: Price) extends CatalogItemEntity

  given RootJsonFormat[CatalogItemUpdateEntity] = jsonFormat3(CatalogItemUpdateEntity.apply)

  final case class CatalogItemRemovalEntity(id: CatalogItemId, store: Store) extends CatalogItemEntity

  given RootJsonFormat[CatalogItemRemovalEntity] = jsonFormat2(CatalogItemRemovalEntity.apply)
}
