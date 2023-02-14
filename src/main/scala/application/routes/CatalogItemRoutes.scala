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
import spray.json.JsonWriter
import spray.json.enrichAny
import spray.json.enrichString

import application.actors.command.{CatalogItemServerCommand, MessageBrokerCommand}
import application.actors.command.CatalogItemServerCommand.*
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity, given}
import application.routes.entities.Response
import application.routes.Routes.RequestFailed
import application.routes.entities.CatalogItemEntity.*
import application.routes.entities.Response.{CatalogItemResponse, EmptyResponse}
import application.Serializers.given
import application.actors.command.MessageBrokerCommand.CatalogItemPutInPlace
import items.catalogitem.entities.{CatalogItem, LiftedCatalogItem}
import items.catalogitem.Repository.CatalogItemNotFound
import items.catalogitem.domainevents.CatalogItemPutInPlace as CatalogItemPutInPlaceEvent

private object CatalogItemRoutes extends SprayJsonSupport with DefaultJsonProtocol with Directives {

  private given timeout: Timeout = 30.seconds

  private def route[A: FromRequestUnmarshaller, B <: CatalogItemServerCommand, C <: Response[D], D: JsonWriter](
    server: ActorRef[CatalogItemServerCommand],
    request: A => ActorRef[C] => B,
    responseHandler: C => Route
  )(
    using
    ActorSystem[_]
  ): Route =
    entity(as[A]) { e =>
      onComplete(server ? request(e)) {
        case Failure(_) => complete(StatusCodes.InternalServerError, ErrorResponseEntity(RequestFailed))
        case Success(value) => responseHandler(value)
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
          case Success(value) =>
            value.result match {
              case Right(value) => complete(ResultResponseEntity(value))
              case Left(error) => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
            }
        }
      }
    },
    path("catalog_item" / "put_in_place") {
      handleWebSocketMessages {
        Flow[Message]
          .mapAsync(parallelism = 2) {
            case t: TextMessage => t.toStrict(timeout.duration)
            case b: BinaryMessage =>
              b.dataStream.runWith(Sink.ignore)
              Future.failed[TextMessage.Strict](IllegalStateException())
          }
          .mapConcat(t =>
            val json: JsValue = t.text.parseJson
            json.asJsObject.getFields("type") match {
              case Seq(JsString("CatalogItemPutInPlace")) => Seq(json.convertTo[CatalogItemPutInPlaceEvent])
              case _ => Nil
            }
          )
          .mapAsync[EmptyResponse](parallelism = 2)(e => messageBrokerActor ? (CatalogItemPutInPlace(e, _)))
          .mapConcat(_.result match {
            case Left(value) => TextMessage(ErrorResponseEntity(value).toJson.compactPrint) :: Nil
            case Right(value) => TextMessage(ResultResponseEntity(value).toJson.compactPrint) :: Nil
          })
      }
    },
    path("catalog_item") {
      concat(
        get {
          route[CatalogItemShowEntity, ShowCatalogItem, CatalogItemResponse, CatalogItem](
            server,
            e => ShowCatalogItem(e.id, e.store, _),
            _.result match {
              case Right(value) => complete(ResultResponseEntity(value))
              case Left(error) =>
                error match {
                  case CatalogItemNotFound => complete(StatusCodes.NotFound, ErrorResponseEntity(CatalogItemNotFound))
                  case _ => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
                }
            }
          )
        },
        post {
          route[CatalogItemAdditionEntity, AddCatalogItem, CatalogItemResponse, CatalogItem](
            server,
            e => AddCatalogItem(e.itemCategoryId, e.store, e.price, _),
            _.result match {
              case Right(value) => complete(ResultResponseEntity(value))
              case Left(error) => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
            }
          )
        },
        put {
          route[CatalogItemUpdateEntity, UpdateCatalogItem, CatalogItemResponse, CatalogItem](
            server,
            e => UpdateCatalogItem(e.id, e.store, e.price, _),
            _.result match {
              case Right(value) => complete(ResultResponseEntity(value))
              case Left(error) =>
                error match {
                  case CatalogItemNotFound => complete(StatusCodes.NotFound, ErrorResponseEntity(CatalogItemNotFound))
                  case _ => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
                }
            }
          )
        },
        delete {
          route[CatalogItemRemovalEntity, RemoveCatalogItem, EmptyResponse, Unit](
            server,
            e => RemoveCatalogItem(e.id, e.store, _),
            _.result match {
              case Right(value) => complete(ResultResponseEntity(value))
              case Left(error) =>
                error match {
                  case CatalogItemNotFound => complete(StatusCodes.NotFound, ErrorResponseEntity(CatalogItemNotFound))
                  case _ => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
                }
            }
          )
        }
      )
    }
  )
}
