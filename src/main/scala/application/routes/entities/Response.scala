/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes.entities

import items.catalogitem.entities.{CatalogItem, LiftedCatalogItem}
import items.item.entities.Item
import items.itemcategory.entities.ItemCategory

sealed trait Response[A] {

  val result: Validated[A]
}

object Response {

  final case class ItemCategoryResponse(result: Validated[ItemCategory]) extends Response[ItemCategory]

  final case class CatalogItemResponse(result: Validated[CatalogItem]) extends Response[CatalogItem]

  final case class LiftedCatalogItemSetResponse(result: Validated[Set[Validated[CatalogItem]]])
    extends Response[Set[Validated[CatalogItem]]]

  final case class ItemResponse(result: Validated[Item]) extends Response[Item]

  final case class ReturnedItemSetResponse(result: Validated[Set[Validated[Item]]]) extends Response[Set[Validated[Item]]]

  final case class EmptyResponse(result: Validated[Unit]) extends Response[Unit]
}
