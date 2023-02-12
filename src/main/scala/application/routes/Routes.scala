/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.*
import spray.json.DefaultJsonProtocol

import application.actors.command.{CatalogItemServerCommand, ItemCategoryServerCommand}
import application.routes.entities.Entity.ErrorResponseEntity
import items.itemcategory.entities.ItemCategory

object Routes extends Directives with SprayJsonSupport with DefaultJsonProtocol {

  case object RequestFailed extends ValidationError {

    override val message: String = "An error has occurred while processing the request"
  }

  case class DeserializationFailed(message: String) extends ValidationError

  private val rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case MalformedRequestContentRejection(msg, _) =>
          complete(StatusCodes.BadRequest, ErrorResponseEntity(DeserializationFailed(msg)))
      }
      .result()

  def apply(
    itemCategoryServer: ActorRef[ItemCategoryServerCommand],
    catalogItemServer: ActorRef[CatalogItemServerCommand]
  )(
    using
    ActorSystem[_]
  ): Route = handleRejections(rejectionHandler) {
    concat(ItemCategoryRoutes(itemCategoryServer), CatalogItemRoutes(catalogItemServer))
  }
}
