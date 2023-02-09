/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors.command

import akka.actor.typed.ActorRef

import application.routes.entities.Response.{EmptyResponse, ItemCategoryResponse}
import items.itemcategory.valueobjects.*

sealed trait ItemCategoryServerCommand

object ItemCategoryServerCommand {

  final case class ShowItemCategory(id: ItemCategoryId, replyTo: ActorRef[ItemCategoryResponse]) extends ItemCategoryServerCommand

  final case class AddItemCategory(name: Name, description: Description, replyTo: ActorRef[ItemCategoryResponse])
    extends ItemCategoryServerCommand

  final case class UpdateItemCategory(
    id: ItemCategoryId,
    newName: Name,
    newDescription: Description,
    replyTo: ActorRef[ItemCategoryResponse]
  ) extends ItemCategoryServerCommand

  final case class RemoveItemCategory(
    id: ItemCategoryId,
    replyTo: ActorRef[EmptyResponse]
  ) extends ItemCategoryServerCommand
}
