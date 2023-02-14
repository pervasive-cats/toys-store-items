/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes

import java.util.concurrent.CountDownLatch

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.*
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.testkit.WSProbe
import org.scalatest.EitherValues.*
import org.scalatest.OptionValues.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import spray.json.enrichAny

import application.actors.command.*
import application.actors.command.CatalogItemServerCommand.*
import application.routes.entities.CatalogItemEntity.*
import application.routes.entities.Response.*
import application.Serializers.given
import application.actors.command.MessageBrokerCommand.CatalogItemPutInPlace
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity, given}
import application.routes.Routes.DeserializationFailed
import items.catalogitem.entities.*
import items.catalogitem.valueobjects.*
import items.catalogitem.Repository.CatalogItemNotFound
import items.catalogitem.entities.CatalogItemOps.updated
import items.itemcategory.valueobjects.ItemCategoryId
import items.RepositoryOperationFailed
import items.catalogitem.domainevents.CatalogItemPutInPlace as CatalogItemPutInPlaceEvent

class CatalogItemRoutesTest extends AnyFunSpec with ScalatestRouteTest with SprayJsonSupport {

  private given typedSystem: ActorSystem[_] = system.toTyped
  private val catalogItemServerProbe = TestProbe[CatalogItemServerCommand]()
  private val messageBrokerActorProbe = TestProbe[MessageBrokerCommand]()

  private val routes: Route =
    Routes(messageBrokerActorProbe.ref, TestProbe[ItemCategoryServerCommand]().ref, catalogItemServerProbe.ref)

  private val catalogItemId: CatalogItemId = CatalogItemId(1).getOrElse(fail())
  private val itemCategoryId: ItemCategoryId = ItemCategoryId(1).getOrElse(fail())
  private val store: Store = Store(1).getOrElse(fail())

  private val price: Price = Price(
    Amount(12.99).getOrElse(fail()),
    Currency.EUR
  )
  private val inPlaceCatalogItem: InPlaceCatalogItem = InPlaceCatalogItem(catalogItemId, itemCategoryId, store, price)
  private val count: Count = Count(2).getOrElse(fail())
  private val liftedCatalogItem: LiftedCatalogItem = LiftedCatalogItem(catalogItemId, itemCategoryId, store, price, count)

  private def checkInPlaceCatalogItem(actualCatalogItem: CatalogItem, effectiveCatalogItem: InPlaceCatalogItem): Unit =
    actualCatalogItem match {
      case i: InPlaceCatalogItem =>
        i.id shouldBe effectiveCatalogItem.id
        i.category shouldBe effectiveCatalogItem.category
        i.price shouldBe effectiveCatalogItem.price
        i.store shouldBe effectiveCatalogItem.store
      case _ => fail()
    }

  private def checkLiftedCatalogItem(actualCatalogItem: CatalogItem, effectiveCatalogItem: LiftedCatalogItem): Unit =
    actualCatalogItem match {
      case i: LiftedCatalogItem =>
        i.id shouldBe effectiveCatalogItem.id
        i.category shouldBe effectiveCatalogItem.category
        i.price shouldBe effectiveCatalogItem.price
        i.store shouldBe effectiveCatalogItem.store
        i.count shouldBe effectiveCatalogItem.count
      case _ => fail()
    }

  describe("A catalog item service") {
    describe("when sending a GET request to the /catalog_item/lifted endpoint") {
      it("should send a response returning all lifted catalog items if everything is correct") {
        val test: RouteTestResult = Get("/catalog_item/lifted") ~> routes
        val message: CatalogItemServerCommand = catalogItemServerProbe.receiveMessage(10.seconds)
        message match {
          case ShowAllLiftedCatalogItems(r) =>
            r ! LiftedCatalogItemSetResponse(
              Right[ValidationError, Set[Validated[CatalogItem]]](
                Set(
                  Right[ValidationError, CatalogItem](liftedCatalogItem)
                )
              )
            )
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          val result: Set[Validated[CatalogItem]] = entityAs[ResultResponseEntity[Set[Validated[CatalogItem]]]].result
          result should have size 1
          checkLiftedCatalogItem(result.headOption.value.value, liftedCatalogItem)
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult = Get("/catalog_item/lifted") ~> routes
        val message: ShowAllLiftedCatalogItems = catalogItemServerProbe.expectMessageType[ShowAllLiftedCatalogItems](10.seconds)
        message.replyTo ! LiftedCatalogItemSetResponse(
          Left[ValidationError, Set[Validated[CatalogItem]]](RepositoryOperationFailed)
        )
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }
    }

    describe("when sending a websocket request to the /catalog_item/put_in_place endpoint") {
      it("should send a response returning success if everything is correct") {
        val wsProbe: WSProbe = WSProbe()
        WS("/catalog_item/put_in_place", wsProbe.flow) ~> routes ~> check {
          val event: CatalogItemPutInPlaceEvent = CatalogItemPutInPlaceEvent(catalogItemId, store)
          wsProbe.sendMessage(event.toJson.compactPrint)
          val latch: CountDownLatch = CountDownLatch(2)
          Future {
            val message: MessageBrokerCommand = messageBrokerActorProbe.receiveMessage(10.seconds)
            message match {
              case CatalogItemPutInPlace(e, r) =>
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
        WS("/catalog_item/put_in_place", wsProbe.flow) ~> routes ~> check {
          val event: CatalogItemPutInPlaceEvent = CatalogItemPutInPlaceEvent(catalogItemId, store)
          wsProbe.sendMessage(event.toJson.compactPrint)
          val latch: CountDownLatch = CountDownLatch(2)
          Future {
            val message: CatalogItemPutInPlace = messageBrokerActorProbe.expectMessageType[CatalogItemPutInPlace](10.seconds)
            message.replyTo ! EmptyResponse(Left[ValidationError, Unit](CatalogItemNotFound))
            latch.countDown()
          }
          Future {
            wsProbe.expectMessage(ErrorResponseEntity(CatalogItemNotFound).toJson.compactPrint)
            latch.countDown()
          }
          latch.await()
        }
      }
    }

    describe("when sending a GET request to the /catalog_item endpoint") {
      it("should send a response returning a catalog item if everything is correct") {
        val test: RouteTestResult =
          Get("/catalog_item", CatalogItemShowEntity(catalogItemId, store)) ~> routes
        val message: CatalogItemServerCommand = catalogItemServerProbe.receiveMessage(10.seconds)
        message match {
          case ShowCatalogItem(i, s, r) =>
            i shouldBe catalogItemId
            s shouldBe store
            r ! CatalogItemResponse(Right[ValidationError, CatalogItem](inPlaceCatalogItem))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkInPlaceCatalogItem(entityAs[ResultResponseEntity[CatalogItem]].result, inPlaceCatalogItem)
        }
      }

      it("should send a 404 response if the catalog item does not exists") {
        val test: RouteTestResult =
          Get("/catalog_item", CatalogItemShowEntity(catalogItemId, store)) ~> routes
        val message: ShowCatalogItem = catalogItemServerProbe.expectMessageType[ShowCatalogItem](10.seconds)
        message.replyTo ! CatalogItemResponse(Left[ValidationError, CatalogItem](CatalogItemNotFound))
        test ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe CatalogItemNotFound
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult =
          Get("/catalog_item", CatalogItemShowEntity(catalogItemId, store)) ~> routes
        val message: ShowCatalogItem = catalogItemServerProbe.expectMessageType[ShowCatalogItem](10.seconds)
        message.replyTo ! CatalogItemResponse(Left[ValidationError, CatalogItem](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Delete("/catalog_item", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'id'")
        }
      }
    }

    describe("when sending a POST request to the /catalog_item endpoint") {
      it("should send a response creating a new catalog item if everything is correct") {
        val test: RouteTestResult =
          Post("/catalog_item", CatalogItemAdditionEntity(itemCategoryId, store, price)) ~> routes
        val message: CatalogItemServerCommand = catalogItemServerProbe.receiveMessage(10.seconds)
        message match {
          case AddCatalogItem(c, s, p, r) =>
            c shouldBe itemCategoryId
            s shouldBe store
            p shouldBe price
            r ! CatalogItemResponse(Right[ValidationError, CatalogItem](inPlaceCatalogItem))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkInPlaceCatalogItem(entityAs[ResultResponseEntity[CatalogItem]].result, inPlaceCatalogItem)
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult =
          Post("/catalog_item", CatalogItemAdditionEntity(itemCategoryId, store, price)) ~> routes
        val message: AddCatalogItem = catalogItemServerProbe.expectMessageType[AddCatalogItem](10.seconds)
        message.replyTo ! CatalogItemResponse(Left[ValidationError, CatalogItem](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Post("/catalog_item", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'itemCategoryId'")
        }
      }
    }

    describe("when sending a DELETE request to the /catalog_item endpoint") {
      it("should send a response removing a catalog item if everything is correct") {
        val test: RouteTestResult =
          Delete("/catalog_item", CatalogItemRemovalEntity(catalogItemId, store)) ~> routes
        val message: CatalogItemServerCommand = catalogItemServerProbe.receiveMessage(10.seconds)
        message match {
          case RemoveCatalogItem(i, s, r) =>
            i shouldBe catalogItemId
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
          Delete("/catalog_item", CatalogItemRemovalEntity(catalogItemId, store)) ~> routes
        val message: RemoveCatalogItem = catalogItemServerProbe.expectMessageType[RemoveCatalogItem](10.seconds)
        message.replyTo ! EmptyResponse(Left[ValidationError, Unit](CatalogItemNotFound))
        test ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe CatalogItemNotFound
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult =
          Delete("/catalog_item", CatalogItemRemovalEntity(catalogItemId, store)) ~> routes
        val message: RemoveCatalogItem = catalogItemServerProbe.expectMessageType[RemoveCatalogItem](10.seconds)
        message.replyTo ! EmptyResponse(Left[ValidationError, Unit](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Delete("/catalog_item", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'id'")
        }
      }
    }

    describe("when sending a PUT request to the /catalog_item endpoint") {
      val newPrice: Price = Price(
        Amount(150.99).getOrElse(fail()),
        Currency.GBP
      )
      val newInPlaceCatalogItem: InPlaceCatalogItem = inPlaceCatalogItem.updated(newPrice)
      val newLiftedCatalogItem: LiftedCatalogItem = liftedCatalogItem.updated(newPrice)
      it("should send a response updating a catalog item if everything is correct") {
        val firstTest: RouteTestResult =
          Put("/catalog_item", CatalogItemUpdateEntity(catalogItemId, store, newPrice)) ~> routes
        val firstMessage: CatalogItemServerCommand = catalogItemServerProbe.receiveMessage(10.seconds)
        firstMessage match {
          case UpdateCatalogItem(i, s, p, r) =>
            i shouldBe catalogItemId
            s shouldBe store
            p shouldBe newPrice
            r ! CatalogItemResponse(Right[ValidationError, CatalogItem](newInPlaceCatalogItem))
          case _ => fail()
        }
        firstTest ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkInPlaceCatalogItem(entityAs[ResultResponseEntity[CatalogItem]].result, newInPlaceCatalogItem)
        }
        val secondTest: RouteTestResult =
          Put("/catalog_item", CatalogItemUpdateEntity(catalogItemId, store, newPrice)) ~> routes
        val secondMessage: CatalogItemServerCommand = catalogItemServerProbe.receiveMessage(10.seconds)
        secondMessage match {
          case UpdateCatalogItem(i, s, p, r) =>
            i shouldBe catalogItemId
            s shouldBe store
            p shouldBe newPrice
            r ! CatalogItemResponse(Right[ValidationError, CatalogItem](newLiftedCatalogItem))
          case _ => fail()
        }
        secondTest ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkLiftedCatalogItem(entityAs[ResultResponseEntity[CatalogItem]].result, newLiftedCatalogItem)
        }
      }

      it("should send a 404 response if the catalog item does not exists") {
        val test: RouteTestResult =
          Put("/catalog_item", CatalogItemUpdateEntity(catalogItemId, store, price)) ~> routes
        val message: UpdateCatalogItem = catalogItemServerProbe.expectMessageType[UpdateCatalogItem](10.seconds)
        message.replyTo ! CatalogItemResponse(Left[ValidationError, CatalogItem](CatalogItemNotFound))
        test ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe CatalogItemNotFound
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult =
          Put("/catalog_item", CatalogItemUpdateEntity(catalogItemId, store, price)) ~> routes
        val message: UpdateCatalogItem = catalogItemServerProbe.expectMessageType[UpdateCatalogItem](10.seconds)
        message.replyTo ! CatalogItemResponse(Left[ValidationError, CatalogItem](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Put("/catalog_item", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'id'")
        }
      }
    }
  }
}
