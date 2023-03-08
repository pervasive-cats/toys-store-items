/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.getquill.JdbcContextConfig
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import application.actors.command.{CatalogItemServerCommand, RootCommand}
import application.actors.command.CatalogItemServerCommand.*
import application.actors.command.RootCommand.Startup
import application.routes.entities.Response.{CatalogItemResponse, EmptyResponse}
import items.catalogitem.valueobjects.*
import items.catalogitem.Repository.CatalogItemNotFound
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.itemcategory.valueobjects.ItemCategoryId
import items.catalogitem.entities.CatalogItemOps.lift

class CatalogItemServerActorTest extends AnyFunSpec with TestContainerForAll with BeforeAndAfterAll {

  private val timeout: FiniteDuration = 300.seconds

  override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:15.1"),
    databaseName = "items",
    username = "test",
    password = "test",
    commonJdbcParams = CommonParams(timeout, timeout, Some("items.sql"))
  )

  private val testKit: ActorTestKit = ActorTestKit()
  private val rootActorProbe: TestProbe[RootCommand] = testKit.createTestProbe[RootCommand]()
  private val catalogItemResponseProbe: TestProbe[CatalogItemResponse] = testKit.createTestProbe[CatalogItemResponse]()
  private val emptyResponseProbe: TestProbe[EmptyResponse] = testKit.createTestProbe[EmptyResponse]()

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var catalogItemServer: Option[ActorRef[CatalogItemServerCommand]] = None

  override def afterContainersStart(containers: Containers): Unit =
    catalogItemServer = Some(
      testKit.spawn(
        CatalogItemServerActor(
          rootActorProbe.ref,
          JdbcContextConfig(
          ConfigFactory
            .load()
            .getConfig("repository")
            .withValue(
              "dataSource.portNumber",
              ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue())
            )
          ).dataSource
        )
      )
    )

  override def afterAll(): Unit = testKit.shutdownTestKit()

  private val catalogItemId: CatalogItemId = CatalogItemId(1000).getOrElse(fail())
  private val itemCategoryId: ItemCategoryId = ItemCategoryId(1000).getOrElse(fail())
  private val store: Store = Store(1000).getOrElse(fail())
  private val price: Price = Price(Amount(15.99).getOrElse(fail()), Currency.EUR)

  private def checkInPlaceCatalogItem(itemCategoryId: ItemCategoryId, store: Store, price: Price): CatalogItemId =
    catalogItemResponseProbe.expectMessageType[CatalogItemResponse](10.seconds).result match {
      case Right(i: InPlaceCatalogItem) =>
        i.category shouldBe itemCategoryId
        i.store shouldBe store
        i.price shouldBe price
        i.id
      case _ => fail()
    }

  private def checkLiftedCatalogItem(itemCategoryId: ItemCategoryId, store: Store, count: Count, price: Price): CatalogItemId =
    catalogItemResponseProbe.expectMessageType[CatalogItemResponse](10.seconds).result match {
      case Right(i: LiftedCatalogItem) =>
        i.category shouldBe itemCategoryId
        i.store shouldBe store
        i.price shouldBe price
        i.count shouldBe count
        i.id
      case _ => fail()
    }

  describe("An item category server actor") {
    describe("when first started up") {
      it("should notify the root actor of its start") {
        rootActorProbe.expectMessage(10.seconds, Startup(true))
      }
    }
  }

  describe("A catalog item") {
    describe("after being added") {
      it("should be present in the database") {
        val server: ActorRef[CatalogItemServerCommand] = catalogItemServer.getOrElse(fail())
        server ! AddCatalogItem(itemCategoryId, store, price, catalogItemResponseProbe.ref)
        val catalogItemId: CatalogItemId = checkInPlaceCatalogItem(itemCategoryId, store, price)
        server ! ShowCatalogItem(catalogItemId, store, catalogItemResponseProbe.ref)
        checkInPlaceCatalogItem(itemCategoryId, store, price)
        server ! RemoveCatalogItem(catalogItemId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("after being added and while trying to find it with the wrong id") {
      it("should not be present in the database") {
        val server: ActorRef[CatalogItemServerCommand] = catalogItemServer.getOrElse(fail())
        server ! AddCatalogItem(itemCategoryId, store, price, catalogItemResponseProbe.ref)
        val catalogItemId: CatalogItemId = checkInPlaceCatalogItem(itemCategoryId, store, price)
        val wrongCatalogItemId: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
        assume(catalogItemId !== wrongCatalogItemId)
        server ! ShowCatalogItem(wrongCatalogItemId, store, catalogItemResponseProbe.ref)
        catalogItemResponseProbe.expectMessage(
          10.seconds,
          CatalogItemResponse(Left[ValidationError, CatalogItem](CatalogItemNotFound))
        )
        server ! RemoveCatalogItem(catalogItemId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("after being added and then removed") {
      it("should not be present in the database") {
        val server: ActorRef[CatalogItemServerCommand] = catalogItemServer.getOrElse(fail())
        server ! AddCatalogItem(itemCategoryId, store, price, catalogItemResponseProbe.ref)
        val catalogItemId: CatalogItemId = checkInPlaceCatalogItem(itemCategoryId, store, price)
        server ! RemoveCatalogItem(catalogItemId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
        server ! ShowCatalogItem(catalogItemId, store, catalogItemResponseProbe.ref)
        catalogItemResponseProbe.expectMessage(
          10.seconds,
          CatalogItemResponse(Left[ValidationError, CatalogItem](CatalogItemNotFound))
        )
      }
    }

    describe("after being added and while trying to get deleted from the database with the wrong id") {
      it("should not be allowed") {
        val server: ActorRef[CatalogItemServerCommand] = catalogItemServer.getOrElse(fail())
        server ! AddCatalogItem(itemCategoryId, store, price, catalogItemResponseProbe.ref)
        val catalogItemId: CatalogItemId = checkInPlaceCatalogItem(itemCategoryId, store, price)
        val wrongCatalogItemId: CatalogItemId = CatalogItemId(9000).getOrElse(fail())
        assume(catalogItemId !== wrongCatalogItemId)
        server ! RemoveCatalogItem(wrongCatalogItemId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Left[ValidationError, Unit](CatalogItemNotFound)))
        server ! RemoveCatalogItem(catalogItemId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("after being added and then its data gets updated") {
      it("should show the update") {
        val server: ActorRef[CatalogItemServerCommand] = catalogItemServer.getOrElse(fail())
        val newPrice: Price = Price(Amount(150.99).getOrElse(fail()), Currency.GBP)
        server ! AddCatalogItem(itemCategoryId, store, price, catalogItemResponseProbe.ref)
        val catalogItemId: CatalogItemId = checkInPlaceCatalogItem(itemCategoryId, store, price)
        server ! UpdateCatalogItem(
          catalogItemId,
          store,
          newPrice,
          catalogItemResponseProbe.ref
        )
        val newCatalogItemId: CatalogItemId = checkInPlaceCatalogItem(itemCategoryId, store, newPrice)
        newCatalogItemId shouldBe catalogItemId
        server ! ShowCatalogItem(catalogItemId, store, catalogItemResponseProbe.ref)
        checkInPlaceCatalogItem(itemCategoryId, store, newPrice)
        server ! RemoveCatalogItem(newCatalogItemId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("when their data gets updated but they were never registered in the first place") {
      it("should not be allowed") {
        val server: ActorRef[CatalogItemServerCommand] = catalogItemServer.getOrElse(fail())
        val newPrice: Price = Price(Amount(150.99).getOrElse(fail()), Currency.GBP)
        server ! UpdateCatalogItem(
          catalogItemId,
          store,
          newPrice,
          catalogItemResponseProbe.ref
        )
        catalogItemResponseProbe.expectMessage(
          10.seconds,
          CatalogItemResponse(Left[ValidationError, CatalogItem](CatalogItemNotFound))
        )
      }
    }

    describe("if never registered") {
      it("should not be present") {
        val server: ActorRef[CatalogItemServerCommand] = catalogItemServer.getOrElse(fail())
        server ! ShowCatalogItem(catalogItemId, store, catalogItemResponseProbe.ref)
        catalogItemResponseProbe.expectMessage(
          10.seconds,
          CatalogItemResponse(Left[ValidationError, CatalogItem](CatalogItemNotFound))
        )
        server ! RemoveCatalogItem(catalogItemId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Left[ValidationError, Unit](CatalogItemNotFound)))
      }
    }

    describe("if already registered") {
      it("should allow a new registration") {
        val server: ActorRef[CatalogItemServerCommand] = catalogItemServer.getOrElse(fail())
        server ! AddCatalogItem(itemCategoryId, store, price, catalogItemResponseProbe.ref)
        val catalogItemId: CatalogItemId = checkInPlaceCatalogItem(itemCategoryId, store, price)
        server ! AddCatalogItem(itemCategoryId, store, price, catalogItemResponseProbe.ref)
        val secondCatalogItemId: CatalogItemId = checkInPlaceCatalogItem(itemCategoryId, store, price)
        catalogItemId should not be secondCatalogItemId
      }
    }
  }
}
