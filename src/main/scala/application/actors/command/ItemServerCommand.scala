/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors.command

import application.routes.entities.Response.*
import items.catalogitem.valueobjects.{CatalogItemId, Store}
import items.item.valueobjects.ItemId
import items.itemcategory.valueobjects.ItemCategoryId

import akka.actor.typed.ActorRef

sealed trait ItemServerCommand

object ItemServerCommand {

  final case class ShowItem(id: ItemId, kind: CatalogItemId, store: Store, replyTo: ActorRef[ItemResponse])
    extends ItemServerCommand

  final case class ShowAllReturnedItems(replyTo: ActorRef[ReturnedItemSetResponse]) extends ItemServerCommand

  final case class AddItem(
    id: ItemId,
    kind: CatalogItemId,
    store: Store,
    replyTo: ActorRef[ItemResponse]
  ) extends ItemServerCommand

  final case class RemoveItem(
    id: ItemId,
    kind: CatalogItemId,
    store: Store,
    replyTo: ActorRef[EmptyResponse]
  ) extends ItemServerCommand
}
