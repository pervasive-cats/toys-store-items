/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes

import java.nio.charset.StandardCharsets

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.*
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import spray.json.DefaultJsonProtocol
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.JsonWriter
import spray.json.enrichAny
import spray.json.enrichString

import application.actors.command.{ItemServerCommand, MessageBrokerCommand}
import application.actors.command.ItemServerCommand.*
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity, given}
import application.routes.entities.Response
import application.routes.Routes.{completeWithValidated, route, strictTextMessageFlow, toTextMessage, RequestFailed}
import application.routes.entities.ItemEntity.*
import application.routes.entities.Response.{EmptyResponse, ItemResponse}
import application.Serializers.given
import application.actors.command.MessageBrokerCommand.{ItemAddedToCart, ItemPutInPlace}
import items.item.Repository.{ItemAlreadyPresent, ItemNotFound}
import items.item.domainevents.ItemPutInPlace as ItemPutInPlaceEvent
import items.item.entities.Item

private object ItemRoutes extends SprayJsonSupport with DefaultJsonProtocol with Directives {

  private given timeout: Timeout = 30.seconds

  private def handleItemNotFoundError[A: JsonFormat](response: Response[A]): Route = response.result match {
    case Right(value) => complete(ResultResponseEntity(value))
    case Left(error) =>
      error match {
        case ItemNotFound => complete(StatusCodes.NotFound, ErrorResponseEntity(ItemNotFound))
        case _ => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
      }
  }

  def apply(
    server: ActorRef[ItemServerCommand],
    messageBrokerActor: ActorRef[MessageBrokerCommand]
  )(
    using
    ActorSystem[_]
  ): Route = concat(
    path("item" / "returned") {
      get {
        onComplete(server ? ShowAllReturnedItems.apply) {
          case Failure(_) => complete(StatusCodes.InternalServerError, ErrorResponseEntity(RequestFailed))
          case Success(value) => completeWithValidated(value.result)
        }
      }
    },
    path("item" / "put_in_place") {
      handleWebSocketMessages {
        strictTextMessageFlow
          .mapConcat(t =>
            val json: JsValue = t.text.parseJson
            json.asJsObject.getFields("type") match {
              case Seq(JsString("ItemPutInPlace")) => Seq(json.convertTo[ItemPutInPlaceEvent])
              case _ => Nil
            }
          )
          .mapAsync[EmptyResponse](parallelism = 2)(e => messageBrokerActor ? (ItemPutInPlace(e, _)))
          .map(toTextMessage)
      }
    },
    path("item") {
      concat(
        get {
          route[ItemShowEntity, ShowItem, ItemResponse, Item](
            server,
            e => ShowItem(e.id, e.kind, e.store, _),
            handleItemNotFoundError
          )
        },
        post {
          route[ItemAdditionEntity, AddItem, ItemResponse, Item](
            server,
            e => AddItem(e.id, e.kind, e.store, _),
            _.result match {
              case Right(value) => complete(ResultResponseEntity(value))
              case Left(error) =>
                error match {
                  case ItemAlreadyPresent => complete(StatusCodes.BadRequest, ErrorResponseEntity(ItemAlreadyPresent))
                  case _ => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
                }
            }
          )
        },
        delete {
          route[ItemRemovalEntity, RemoveItem, EmptyResponse, Unit](
            server,
            e => RemoveItem(e.id, e.kind, e.store, _),
            handleItemNotFoundError
          )
        }
      )
    }
  )
}
