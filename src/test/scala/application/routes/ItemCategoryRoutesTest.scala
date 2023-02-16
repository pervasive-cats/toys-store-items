/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes

import scala.concurrent.duration.DurationInt

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.*
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import spray.json.enrichAny

import application.actors.command.{CatalogItemServerCommand, ItemCategoryServerCommand, ItemServerCommand, MessageBrokerCommand}
import application.actors.command.ItemCategoryServerCommand.*
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity, given}
import application.routes.entities.ItemCategoryEntity.*
import application.routes.entities.Response.{EmptyResponse, ItemCategoryResponse}
import application.Serializers.given
import application.routes.Routes.DeserializationFailed
import items.itemcategory.entities.ItemCategory
import items.itemcategory.valueobjects.*
import items.RepositoryOperationFailed
import items.itemcategory.Repository.ItemCategoryNotFound
import items.itemcategory.entities.ItemCategoryOps.updated

class ItemCategoryRoutesTest extends AnyFunSpec with ScalatestRouteTest with SprayJsonSupport {

  private given typedSystem: ActorSystem[_] = system.toTyped
  private val itemCategoryServerProbe = TestProbe[ItemCategoryServerCommand]()

  private val routes: Route =
    Routes(
      TestProbe[MessageBrokerCommand]().ref,
      itemCategoryServerProbe.ref,
      TestProbe[CatalogItemServerCommand]().ref,
      TestProbe[ItemServerCommand]().ref
    )

  private val itemCategoryId: ItemCategoryId = ItemCategoryId(1000).getOrElse(fail())
  private val name: Name = Name("Lego Bat-mobile").getOrElse(fail())
  private val description: Description = Description("Long long long description about a product").getOrElse(fail())
  private val itemCategory: ItemCategory = ItemCategory(itemCategoryId, name, description)

  private def checkItemCategory(actualItemCategory: ItemCategory, effectiveItemCategory: ItemCategory): Unit = {
    actualItemCategory.id shouldBe effectiveItemCategory.id
    actualItemCategory.name shouldBe effectiveItemCategory.name
    actualItemCategory.description shouldBe effectiveItemCategory.description
  }

  describe("An item category service") {
    describe("when sending a GET request to the /item_category endpoint") {
      it("should send a response returning an item category if everything is correct") {
        val test: RouteTestResult =
          Get("/item_category", ItemCategoryShowEntity(itemCategoryId)) ~> routes
        val message: ItemCategoryServerCommand = itemCategoryServerProbe.receiveMessage(10.seconds)
        message match {
          case ShowItemCategory(i, r) =>
            i shouldBe itemCategoryId
            r ! ItemCategoryResponse(Right[ValidationError, ItemCategory](itemCategory))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkItemCategory(entityAs[ResultResponseEntity[ItemCategory]].result, itemCategory)
        }
      }

      it("should send a 404 response if the item category does not exists") {
        val test: RouteTestResult =
          Get("/item_category", ItemCategoryShowEntity(itemCategoryId)) ~> routes
        val message: ShowItemCategory = itemCategoryServerProbe.expectMessageType[ShowItemCategory](10.seconds)
        message.replyTo ! ItemCategoryResponse(Left[ValidationError, ItemCategory](ItemCategoryNotFound))
        test ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe ItemCategoryNotFound
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult =
          Get("/item_category", ItemCategoryShowEntity(itemCategoryId)) ~> routes
        val message: ShowItemCategory = itemCategoryServerProbe.expectMessageType[ShowItemCategory](10.seconds)
        message.replyTo ! ItemCategoryResponse(Left[ValidationError, ItemCategory](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Delete("/item_category", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'id'")
        }
      }
    }

    describe("when sending a POST request to the /item_category endpoint") {
      it("should send a response creating a new item category if everything is correct") {
        val test: RouteTestResult =
          Post("/item_category", ItemCategoryAdditionEntity(name, description)) ~> routes
        val message: ItemCategoryServerCommand = itemCategoryServerProbe.receiveMessage(10.seconds)
        message match {
          case AddItemCategory(n, d, r) =>
            n shouldBe name
            d shouldBe description
            r ! ItemCategoryResponse(Right[ValidationError, ItemCategory](itemCategory))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkItemCategory(entityAs[ResultResponseEntity[ItemCategory]].result, itemCategory)
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult =
          Post("/item_category", ItemCategoryAdditionEntity(name, description)) ~> routes
        val message: AddItemCategory = itemCategoryServerProbe.expectMessageType[AddItemCategory](10.seconds)
        message.replyTo ! ItemCategoryResponse(Left[ValidationError, ItemCategory](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Post("/item_category", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'name'")
        }
      }
    }

    describe("when sending a DELETE request to the /item_category endpoint") {
      it("should send a response removing an item category if everything is correct") {
        val test: RouteTestResult =
          Delete("/item_category", ItemCategoryRemovalEntity(itemCategoryId)) ~> routes
        val message: ItemCategoryServerCommand = itemCategoryServerProbe.receiveMessage(10.seconds)
        message match {
          case RemoveItemCategory(i, r) =>
            i shouldBe itemCategoryId
            r ! EmptyResponse(Right[ValidationError, Unit](()))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ResultResponseEntity[Unit]].result shouldBe ()
        }
      }

      it("should send a 404 response if the item category does not exists") {
        val test: RouteTestResult =
          Delete("/item_category", ItemCategoryRemovalEntity(itemCategoryId)) ~> routes
        val message: RemoveItemCategory = itemCategoryServerProbe.expectMessageType[RemoveItemCategory](10.seconds)
        message.replyTo ! EmptyResponse(Left[ValidationError, Unit](ItemCategoryNotFound))
        test ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe ItemCategoryNotFound
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult =
          Delete("/item_category", ItemCategoryRemovalEntity(itemCategoryId)) ~> routes
        val message: RemoveItemCategory = itemCategoryServerProbe.expectMessageType[RemoveItemCategory](10.seconds)
        message.replyTo ! EmptyResponse(Left[ValidationError, Unit](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Delete("/item_category", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'id'")
        }
      }
    }

    describe("when sending a PUT request to the /item_category endpoint") {
      val newName: Name = Name("Lego Bat-mobile legacy edition").getOrElse(fail())
      val newDescription: Description = Description("Another, different description from the previous one").getOrElse(fail())
      val newItemCategory: ItemCategory = itemCategory.updated(newName, newDescription)

      it("should send a response updating an item category if everything is correct") {
        val test: RouteTestResult =
          Put("/item_category", ItemCategoryUpdateEntity(itemCategoryId, newName, newDescription)) ~> routes
        val message: ItemCategoryServerCommand = itemCategoryServerProbe.receiveMessage(10.seconds)
        message match {
          case UpdateItemCategory(i, n, d, r) =>
            i shouldBe itemCategoryId
            n shouldBe newName
            d shouldBe newDescription
            r ! ItemCategoryResponse(Right[ValidationError, ItemCategory](newItemCategory))
          case _ => fail()
        }
        test ~> check {
          status shouldBe StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          checkItemCategory(entityAs[ResultResponseEntity[ItemCategory]].result, newItemCategory)
        }
      }

      it("should send a 404 response if the item category does not exists") {
        val test: RouteTestResult =
          Put("/item_category", ItemCategoryUpdateEntity(itemCategoryId, newName, newDescription)) ~> routes
        val message: UpdateItemCategory = itemCategoryServerProbe.expectMessageType[UpdateItemCategory](10.seconds)
        message.replyTo ! ItemCategoryResponse(Left[ValidationError, ItemCategory](ItemCategoryNotFound))
        test ~> check {
          status shouldBe StatusCodes.NotFound
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe ItemCategoryNotFound
        }
      }

      it("should send a 500 response if anything else happens") {
        val test: RouteTestResult =
          Put("/item_category", ItemCategoryUpdateEntity(itemCategoryId, newName, newDescription)) ~> routes
        val message: UpdateItemCategory = itemCategoryServerProbe.expectMessageType[UpdateItemCategory](10.seconds)
        message.replyTo ! ItemCategoryResponse(Left[ValidationError, ItemCategory](RepositoryOperationFailed))
        test ~> check {
          status shouldBe StatusCodes.InternalServerError
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
        }
      }

      it("should send a 400 response if the request body is not correctly formatted") {
        Put("/item_category", "{}".toJson) ~> routes ~> check {
          status shouldBe StatusCodes.BadRequest
          contentType shouldBe ContentTypes.`application/json`
          entityAs[ErrorResponseEntity].error shouldBe DeserializationFailed("Object expected in field 'id'")
        }
      }
    }
  }
}
