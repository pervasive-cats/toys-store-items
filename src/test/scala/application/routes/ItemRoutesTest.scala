/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes

import application.actors.command.*
import application.actors.command.ItemServerCommand.*
import application.routes.entities.ItemEntity.*
import application.routes.entities.Response.*
import application.Serializers.given
import application.actors.command.MessageBrokerCommand.ItemPutInPlace
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity, given}
import application.routes.Routes.DeserializationFailed
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem}
import items.catalogitem.valueobjects.*
import items.item.entities.*
import items.item.valueobjects.ItemId
import items.itemcategory.valueobjects.ItemCategoryId
import items.RepositoryOperationFailed
import items.item.Repository.{ItemAlreadyPresent, ItemNotFound}
import items.item.domainevents.ItemPutInPlace as ItemPutInPlaceEvent

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.*
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import org.scalatest.EitherValues.*
import org.scalatest.OptionValues.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import spray.json.enrichAny

import java.util.concurrent.CountDownLatch
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ItemRoutesTest extends AnyFunSpec with ScalatestRouteTest with SprayJsonSupport {

  private given typedSystem: ActorSystem[_] = system.toTyped
  private val itemServerProbe = TestProbe[ItemServerCommand]()
  private val messageBrokerActorProbe = TestProbe[MessageBrokerCommand]()

  private val routes: Route =
    Routes(
      messageBrokerActorProbe.ref,
      TestProbe[ItemCategoryServerCommand]().ref,
      TestProbe[CatalogItemServerCommand]().ref,
      itemServerProbe.ref
    )

  private val id: ItemId = ItemId(1).getOrElse(fail())
  private val itemCategoryId: ItemCategoryId = ItemCategoryId(1).getOrElse(fail())
  private val catalogItemId: CatalogItemId = CatalogItemId(1).getOrElse(fail())
  private val store: Store = Store(1).getOrElse(fail())
  private val price: Price = Price(Amount(12.99).getOrElse(fail()), Currency.EUR)
  private val catalogItem: CatalogItem = InPlaceCatalogItem(catalogItemId, itemCategoryId, store, price)
  private val inPlaceItem: InPlaceItem = InPlaceItem(id, catalogItem)
  private val returnedItem: ReturnedItem = ReturnedItem(id, catalogItem)

  private def checkInPlaceItem(actualItem: Item): Unit =
    actualItem match {
      case i: InPlaceItem =>
        i.id shouldBe id
        i.kind.id shouldBe catalogItemId
        i.kind.category shouldBe itemCategoryId
        i.kind.price shouldBe price
        i.kind.store shouldBe store
      case _ => fail()
    }

  private def checkReturnedItem(actualItem: Item): Unit =
    actualItem match {
      case i: ReturnedItem =>
        i.id shouldBe id
        i.kind.id shouldBe catalogItemId
        i.kind.category shouldBe itemCategoryId
        i.kind.price shouldBe price
        i.kind.store shouldBe store
      case _ => fail()
    }

  describe("An item service") {
    describe("when sending a GET request to the /item/returned endpoint") {
      it("should send a response returning all returned items if everything is correct") {
        val test: RouteTestResult = Get("/item/returned") ~> routes
        val message: ItemServerCommand = itemServerProbe.receiveMessage(10.seconds)
        message match {
          case ShowAllReturnedItems(r) =>
            r ! ReturnedItemSetResponse(
              Right[ValidationError, Set[Validated[Item]]](
                Set(
                  Right[ValidationError, Item](returnedItem)
                )
              )
            )
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          val result: Set[Validated[Item]] = entityAs[ResultResponseEntity[Set[Validated[Item]]]].result
          result should have size 1
          checkReturnedItem(result.headOption.value.value)
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult = Get("/item/returned") ~> routes
        val message: ShowAllReturnedItems = itemServerProbe.expectMessageType[ShowAllReturnedItems](10.seconds)
        message.replyTo ! ReturnedItemSetResponse(
          Left[ValidationError, Set[Validated[Item]]](RepositoryOperationFailed)
        )
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }
    }

    describe("when sending a websocket request to the /item/put_in_place endpoint") {
      it("should send a response returning success if everything is correct") {
        val wsProbe: WSProbe = WSProbe()
        WS("/item/put_in_place", wsProbe.flow) ~> routes ~> check {
          val event: ItemPutInPlaceEvent = ItemPutInPlaceEvent(catalogItemId, store, id)
          wsProbe.sendMessage(event.toJson.compactPrint)
          val latch: CountDownLatch = CountDownLatch(2)
          Future {
            val message: MessageBrokerCommand = messageBrokerActorProbe.receiveMessage(10.seconds)
            message match {
              case ItemPutInPlace(e, r) =>
                e shouldBe event
                r ! EmptyResponse(Right[ValidationError, Unit](()))
              case _ => fail()
            }
            latch.countDown()
          }
          Future {
            wsProbe.expectMessage(ResultResponseEntity[Unit](()).toJson.compactPrint)
            latch.countDown()
          }
          latch.await()
        }
      }

      it("should send a response returning failure is something goes wrong") {
        val wsProbe: WSProbe = WSProbe()
        WS("/item/put_in_place", wsProbe.flow) ~> routes ~> check {
          val event: ItemPutInPlaceEvent = ItemPutInPlaceEvent(catalogItemId, store, id)
          wsProbe.sendMessage(event.toJson.compactPrint)
          val latch: CountDownLatch = CountDownLatch(2)
          Future {
            val message: ItemPutInPlace = messageBrokerActorProbe.expectMessageType[ItemPutInPlace](10.seconds)
            message.replyTo ! EmptyResponse(Left[ValidationError, Unit](ItemNotFound))
            latch.countDown()
          }
          Future {
            wsProbe.expectMessage(ErrorResponseEntity(ItemNotFound).toJson.compactPrint)
            latch.countDown()
          }
          latch.await()
        }
      }
    }

    describe("when sending a GET request to the /item endpoint") {
      it("should send a response returning an item if everything is correct") {
        val test: RouteTestResult =
          Get("/item", ItemShowEntity(id, catalogItemId, store)) ~> routes
        val message: ItemServerCommand = itemServerProbe.receiveMessage(10.seconds)
        message match {
          case ShowItem(i, c, s, r) =>
            i shouldBe id
            c shouldBe catalogItemId
            s shouldBe store
            r ! ItemResponse(Right[ValidationError, Item](inPlaceItem))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkInPlaceItem(entityAs[ResultResponseEntity[Item]].result)
        }
      }

      it("should send a 404 response if the item does not exists") {
        val test: RouteTestResult = Get("/item", ItemShowEntity(id, catalogItemId, store)) ~> routes
        val message: ShowItem = itemServerProbe.expectMessageType[ShowItem](10.seconds)
        message.replyTo ! ItemResponse(Left[ValidationError, Item](ItemNotFound))
        test ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe ItemNotFound
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult =
          Get("/item", ItemShowEntity(id, catalogItemId, store)) ~> routes
        val message: ShowItem = itemServerProbe.expectMessageType[ShowItem](10.seconds)
        message.replyTo ! ItemResponse(Left[ValidationError, Item](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Delete("/item", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'id'")
        }
      }
    }

    describe("when sending a POST request to the /item endpoint") {
      it("should send a response creating a new item if everything is correct") {
        val test: RouteTestResult =
          Post("/item", ItemAdditionEntity(id, catalogItemId, store)) ~> routes
        val message: ItemServerCommand = itemServerProbe.receiveMessage(10.seconds)
        message match {
          case AddItem(i, c, s, r) =>
            i shouldBe id
            c shouldBe catalogItemId
            s shouldBe store
            r ! ItemResponse(Right[ValidationError, Item](inPlaceItem))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkInPlaceItem(entityAs[ResultResponseEntity[Item]].result)
        }
      }

      it("should send a 400 response if the item was already added") {
        val test: RouteTestResult = Post("/item", ItemAdditionEntity(id, catalogItemId, store)) ~> routes
        val message: AddItem = itemServerProbe.expectMessageType[AddItem](10.seconds)
        message.replyTo ! ItemResponse(Left[ValidationError, Item](ItemAlreadyPresent))
        test ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe ItemAlreadyPresent
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult =
          Post("/item", ItemAdditionEntity(id, catalogItemId, store)) ~> routes
        val message: AddItem = itemServerProbe.expectMessageType[AddItem](10.seconds)
        message.replyTo ! ItemResponse(Left[ValidationError, Item](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Post("/item", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'id'")
        }
      }
    }

    describe("when sending a DELETE request to the /item endpoint") {
      it("should send a response removing an item if everything is correct") {
        val test: RouteTestResult =
          Delete("/item", ItemRemovalEntity(id, catalogItemId, store)) ~> routes
        val message: ItemServerCommand = itemServerProbe.receiveMessage(10.seconds)
        message match {
          case RemoveItem(i, c, s, r) =>
            i shouldBe id
            c shouldBe catalogItemId
            s shouldBe store
            r ! EmptyResponse(Right[ValidationError, Unit](()))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ResultResponseEntity[Unit]].result shouldBe ()
        }
      }

      it("should send a 404 response if the catalog item does not exists") {
        val test: RouteTestResult =
          Delete("/item", ItemRemovalEntity(id, catalogItemId, store)) ~> routes
        val message: RemoveItem = itemServerProbe.expectMessageType[RemoveItem](10.seconds)
        message.replyTo ! EmptyResponse(Left[ValidationError, Unit](ItemNotFound))
        test ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe ItemNotFound
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult =
          Delete("/item", ItemRemovalEntity(id, catalogItemId, store)) ~> routes
        val message: RemoveItem = itemServerProbe.expectMessageType[RemoveItem](10.seconds)
        message.replyTo ! EmptyResponse(Left[ValidationError, Unit](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Delete("/item", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'id'")
        }
      }
    }
  }
}
