/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors.command

import akka.actor.typed.ActorRef

import application.routes.entities.Response.{CatalogItemResponse, EmptyResponse, LiftedCatalogItemSetResponse}
import items.catalogitem.valueobjects.{CatalogItemId, Count, Price, Store}
import items.itemcategory.valueobjects.ItemCategoryId

sealed trait CatalogItemServerCommand

object CatalogItemServerCommand {

  final case class ShowCatalogItem(id: CatalogItemId, store: Store, replyTo: ActorRef[CatalogItemResponse])
    extends CatalogItemServerCommand

  final case class ShowAllLiftedCatalogItems(replyTo: ActorRef[LiftedCatalogItemSetResponse]) extends CatalogItemServerCommand

  final case class AddCatalogItem(
    itemCategoryId: ItemCategoryId,
    store: Store,
    price: Price,
    replyTo: ActorRef[CatalogItemResponse]
  ) extends CatalogItemServerCommand

  final case class UpdateCatalogItem(
    id: CatalogItemId,
    store: Store,
    price: Price,
    replyTo: ActorRef[CatalogItemResponse]
  ) extends CatalogItemServerCommand

  final case class RemoveCatalogItem(
    id: CatalogItemId,
    store: Store,
    replyTo: ActorRef[EmptyResponse]
  ) extends CatalogItemServerCommand
}
