/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors.command

import akka.actor.typed.ActorRef

import application.routes.entities.Response.EmptyResponse
import items.catalogitem.domainevents.{
  CatalogItemLifted as CatalogItemLiftedEvent,
  CatalogItemPutInPlace as CatalogItemPutInPlaceEvent
}
import items.catalogitem.valueobjects.{CatalogItemId, Store}

sealed trait MessageBrokerCommand

object MessageBrokerCommand {

  final case class CatalogItemLifted(event: CatalogItemLiftedEvent, replyTo: String, correlationId: String)
    extends MessageBrokerCommand

  final case class CatalogItemPutInPlace(event: CatalogItemPutInPlaceEvent, replyTo: ActorRef[EmptyResponse])
    extends MessageBrokerCommand
}
