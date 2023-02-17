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

import application.actors.command.{CatalogItemServerCommand, MessageBrokerCommand}
import application.actors.command.CatalogItemServerCommand.*
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity, given}
import application.routes.entities.Response
import application.routes.Routes.{completeWithValidated, route, strictTextMessageFlow, toTextMessage, RequestFailed}
import application.routes.entities.CatalogItemEntity.*
import application.routes.entities.Response.{CatalogItemResponse, EmptyResponse}
import application.Serializers.given
import application.actors.command.MessageBrokerCommand.CatalogItemPutInPlace
import items.catalogitem.entities.{CatalogItem, LiftedCatalogItem}
import items.catalogitem.Repository.CatalogItemNotFound
import items.catalogitem.domainevents.CatalogItemPutInPlace as CatalogItemPutInPlaceEvent

private object CatalogItemRoutes extends SprayJsonSupport with DefaultJsonProtocol with Directives {

  private given timeout: Timeout = 30.seconds

  private def handleCatalogItemNotFound[A: JsonFormat](response: Response[A]): Route = response.result match {
    case Right(value) => complete(ResultResponseEntity(value))
    case Left(error) =>
      error match {
        case CatalogItemNotFound => complete(StatusCodes.NotFound, ErrorResponseEntity(CatalogItemNotFound))
        case _ => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
      }
  }

  def apply(
    server: ActorRef[CatalogItemServerCommand],
    messageBrokerActor: ActorRef[MessageBrokerCommand]
  )(
    using
    ActorSystem[_]
  ): Route = concat(
    path("catalog_item" / "lifted") {
      get {
        onComplete(server ? ShowAllLiftedCatalogItems.apply) {
          case Failure(_) => complete(StatusCodes.InternalServerError, ErrorResponseEntity(RequestFailed))
          case Success(value) => completeWithValidated(value.result)
        }
      }
    },
    path("catalog_item" / "put_in_place") {
      handleWebSocketMessages {
        strictTextMessageFlow
          .mapConcat(t =>
            val json: JsValue = t.text.parseJson
            json.asJsObject.getFields("type") match {
              case Seq(JsString("CatalogItemPutInPlace")) => Seq(json.convertTo[CatalogItemPutInPlaceEvent])
              case _ => Nil
            }
          )
          .mapAsync[EmptyResponse](parallelism = 2)(e => messageBrokerActor ? (CatalogItemPutInPlace(e, _)))
          .map(toTextMessage)
      }
    },
    path("catalog_item") {
      concat(
        get {
          route[CatalogItemShowEntity, ShowCatalogItem, CatalogItemResponse, CatalogItem](
            server,
            e => ShowCatalogItem(e.id, e.store, _),
            handleCatalogItemNotFound
          )
        },
        post {
          route[CatalogItemAdditionEntity, AddCatalogItem, CatalogItemResponse, CatalogItem](
            server,
            e => AddCatalogItem(e.itemCategoryId, e.store, e.price, _),
            r => completeWithValidated(r.result)
          )
        },
        put {
          route[CatalogItemUpdateEntity, UpdateCatalogItem, CatalogItemResponse, CatalogItem](
            server,
            e => UpdateCatalogItem(e.id, e.store, e.price, _),
            handleCatalogItemNotFound
          )
        },
        delete {
          route[CatalogItemRemovalEntity, RemoveCatalogItem, EmptyResponse, Unit](
            server,
            e => RemoveCatalogItem(e.id, e.store, _),
            handleCatalogItemNotFound
          )
        }
      )
    }
  )
}
