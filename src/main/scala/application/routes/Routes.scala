/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.*
import akka.http.scaladsl.server.*
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import spray.json.DefaultJsonProtocol
import spray.json.JsonFormat
import spray.json.JsonWriter
import spray.json.enrichAny

import application.actors.command.*
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity}
import application.routes.entities.Response
import application.routes.entities.Response.EmptyResponse
import items.itemcategory.entities.ItemCategory

object Routes extends Directives with SprayJsonSupport with DefaultJsonProtocol {

  case object RequestFailed extends ValidationError {

    override val message: String = "An error has occurred while processing the request"
  }

  case class DeserializationFailed(message: String) extends ValidationError

  private given timeout: Timeout = 30.seconds

  def route[A: FromRequestUnmarshaller, B, C <: Response[D], D: JsonWriter](
    server: ActorRef[B],
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

  def strictTextMessageFlow(using ActorSystem[_]): Flow[Message, TextMessage.Strict, NotUsed] =
    Flow[Message]
      .mapAsync(parallelism = 2) {
        case t: TextMessage => t.toStrict(timeout.duration)
        case b: BinaryMessage =>
          b.dataStream.runWith(Sink.ignore)
          Future.failed[TextMessage.Strict](IllegalStateException())
      }

  def toTextMessage(response: EmptyResponse): TextMessage = response.result match {
    case Left(value) => TextMessage(ErrorResponseEntity(value).toJson.compactPrint)
    case Right(value) => TextMessage(ResultResponseEntity(value).toJson.compactPrint)
  }

  def completeWithValidated[A: JsonFormat](validated: Validated[A]): Route = validated match {
    case Right(value) => complete(ResultResponseEntity(value))
    case Left(error) => complete(StatusCodes.InternalServerError, ErrorResponseEntity(error))
  }

  private val rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case MalformedRequestContentRejection(msg, _) =>
          complete(StatusCodes.BadRequest, ErrorResponseEntity(DeserializationFailed(msg)))
      }
      .result()

  def apply(
    messageBrokerActor: ActorRef[MessageBrokerCommand],
    itemCategoryServer: ActorRef[ItemCategoryServerCommand],
    catalogItemServer: ActorRef[CatalogItemServerCommand],
    itemServer: ActorRef[ItemServerCommand]
  )(
    using
    ActorSystem[_]
  ): Route = handleRejections(rejectionHandler) {
    concat(
      ItemCategoryRoutes(itemCategoryServer),
      CatalogItemRoutes(catalogItemServer, messageBrokerActor),
      ItemRoutes(itemServer, messageBrokerActor)
    )
  }
}
