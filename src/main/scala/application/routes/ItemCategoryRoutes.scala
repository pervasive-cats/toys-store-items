/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.util.Timeout
import spray.json.DefaultJsonProtocol
import spray.json.JsonFormat
import spray.json.JsonWriter

import application.actors.command.ItemCategoryServerCommand
import application.actors.command.ItemCategoryServerCommand.{
  AddItemCategory,
  RemoveItemCategory,
  ShowItemCategory,
  UpdateItemCategory
}
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity}
import application.routes.entities.Response
import application.routes.entities.Response.{EmptyResponse, ItemCategoryResponse}
import application.routes.Routes.{completeWithValidated, route, RequestFailed}
import items.itemcategory.entities.ItemCategory
import items.itemcategory.Repository.ItemCategoryNotFound
import application.routes.entities.ItemCategoryEntity.*
import application.Serializers.given

private object ItemCategoryRoutes extends SprayJsonSupport with DefaultJsonProtocol with Directives {

  private def handleItemCategoryNotFound[A: JsonFormat](response: Response[A]): Route = response.result match {
    case Right(value) => complete(ResultResponseEntity(value))
    case Left(error) =>
      error match {
        case ItemCategoryNotFound => complete(StatusCodes.NotFound, ErrorResponseEntity(ItemCategoryNotFound))
        case _ => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
      }
  }

  def apply(server: ActorRef[ItemCategoryServerCommand])(using ActorSystem[_]): Route =
    path("item_category") {
      concat(
        get {
          route[ItemCategoryShowEntity, ShowItemCategory, ItemCategoryResponse, ItemCategory](
            server,
            e => ShowItemCategory(e.id, _),
            handleItemCategoryNotFound
          )
        },
        post {
          route[ItemCategoryAdditionEntity, AddItemCategory, ItemCategoryResponse, ItemCategory](
            server,
            e => AddItemCategory(e.name, e.description, _),
            r => completeWithValidated(r.result)
          )
        },
        put {
          route[ItemCategoryUpdateEntity, UpdateItemCategory, ItemCategoryResponse, ItemCategory](
            server,
            e => UpdateItemCategory(e.id, e.newName, e.newDescription, _),
            handleItemCategoryNotFound
          )
        },
        delete {
          route[ItemCategoryRemovalEntity, RemoveItemCategory, EmptyResponse, Unit](
            server,
            e => RemoveItemCategory(e.id, _),
            handleItemCategoryNotFound
          )
        }
      )
    }
}
