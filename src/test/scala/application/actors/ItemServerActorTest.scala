/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import javax.sql.DataSource

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.getquill.JdbcContextConfig
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import application.actors.command.{ItemServerCommand, RootCommand}
import application.actors.command.ItemServerCommand.*
import application.actors.command.RootCommand.Startup
import application.routes.entities.Response.{EmptyResponse, ItemResponse}
import items.catalogitem.entities.InPlaceCatalogItem
import items.catalogitem.valueobjects.*
import items.item.entities.{InPlaceItem, Item}
import items.item.valueobjects.ItemId
import items.item.Repository.{ItemAlreadyPresent, ItemNotFound}
import items.itemcategory.valueobjects.ItemCategoryId
import items.catalogitem.Repository as CatalogItemRepository

class ItemServerActorTest extends AnyFunSpec with TestContainerForAll with BeforeAndAfterAll {

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
  private val itemResponseProbe: TestProbe[ItemResponse] = testKit.createTestProbe[ItemResponse]()
  private val emptyResponseProbe: TestProbe[EmptyResponse] = testKit.createTestProbe[EmptyResponse]()

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var itemServer: Option[ActorRef[ItemServerCommand]] = None

  private val itemCategoryId: ItemCategoryId = ItemCategoryId(1000).getOrElse(fail())
  private val store: Store = Store(1000).getOrElse(fail())
  private val price: Price = Price(Amount(15.99).getOrElse(fail()), Currency.EUR)
  private val itemId: ItemId = ItemId(1000).getOrElse(fail())

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var catalogItemId: Option[CatalogItemId] = None

  override def afterContainersStart(containers: Containers): Unit = {
    val dataSource: DataSource =
      JdbcContextConfig(
      ConfigFactory
        .load()
        .getConfig("repository")
        .withValue(
          "dataSource.portNumber",
          ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue())
        )
      ).dataSource
    itemServer = Some(testKit.spawn(ItemServerActor(rootActorProbe.ref, dataSource)))
    val repository: CatalogItemRepository = CatalogItemRepository(dataSource)
    catalogItemId = Some(repository.add(itemCategoryId, store, price).getOrElse(fail()).id)
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()

  private def checkInPlaceItem(): Unit =
    itemResponseProbe.expectMessageType[ItemResponse](10.seconds).result match {
      case Right(i: InPlaceItem) =>
        i.id shouldBe itemId
        i.kind.id shouldBe catalogItemId.getOrElse(fail())
        i.kind.category shouldBe itemCategoryId
        i.kind.price shouldBe price
        i.kind.store shouldBe store
      case _ => fail()
    }

  describe("An item server actor") {
    describe("when first started up") {
      it("should notify the root actor of its start") {
        rootActorProbe.expectMessage(10.seconds, Startup(true))
      }
    }
  }

  describe("An item") {
    describe("after being added") {
      it("should be present in the database") {
        val kindId: CatalogItemId = catalogItemId.getOrElse(fail())
        val server: ActorRef[ItemServerCommand] = itemServer.getOrElse(fail())
        server ! AddItem(itemId, kindId, store, itemResponseProbe.ref)
        checkInPlaceItem()
        server ! ShowItem(itemId, kindId, store, itemResponseProbe.ref)
        checkInPlaceItem()
        server ! RemoveItem(itemId, kindId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("after being added and while trying to find it with the wrong id") {
      it("should not be present in the database") {
        val kindId: CatalogItemId = catalogItemId.getOrElse(fail())
        val server: ActorRef[ItemServerCommand] = itemServer.getOrElse(fail())
        val wrongItemId: ItemId = ItemId(9000).getOrElse(fail())
        server ! AddItem(itemId, kindId, store, itemResponseProbe.ref)
        checkInPlaceItem()
        server ! ShowItem(wrongItemId, kindId, store, itemResponseProbe.ref)
        itemResponseProbe.expectMessage(
          10.seconds,
          ItemResponse(Left[ValidationError, Item](ItemNotFound))
        )
        server ! RemoveItem(itemId, kindId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("after being added and then removed") {
      it("should not be present in the database") {
        val kindId: CatalogItemId = catalogItemId.getOrElse(fail())
        val server: ActorRef[ItemServerCommand] = itemServer.getOrElse(fail())
        server ! AddItem(itemId, kindId, store, itemResponseProbe.ref)
        checkInPlaceItem()
        server ! RemoveItem(itemId, kindId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
        server ! ShowItem(itemId, kindId, store, itemResponseProbe.ref)
        itemResponseProbe.expectMessage(
          10.seconds,
          ItemResponse(Left[ValidationError, Item](ItemNotFound))
        )
      }
    }

    describe("after being added and while trying to get deleted from the database with the wrong id") {
      it("should not be allowed") {
        val kindId: CatalogItemId = catalogItemId.getOrElse(fail())
        val server: ActorRef[ItemServerCommand] = itemServer.getOrElse(fail())
        val wrongItemId: ItemId = ItemId(9000).getOrElse(fail())
        server ! AddItem(itemId, kindId, store, itemResponseProbe.ref)
        checkInPlaceItem()
        server ! RemoveItem(wrongItemId, kindId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Left[ValidationError, Unit](ItemNotFound)))
        server ! RemoveItem(itemId, kindId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("if never registered") {
      it("should not be present") {
        val kindId: CatalogItemId = catalogItemId.getOrElse(fail())
        val server: ActorRef[ItemServerCommand] = itemServer.getOrElse(fail())
        server ! ShowItem(itemId, kindId, store, itemResponseProbe.ref)
        itemResponseProbe.expectMessage(
          10.seconds,
          ItemResponse(Left[ValidationError, Item](ItemNotFound))
        )
        server ! RemoveItem(itemId, kindId, store, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Left[ValidationError, Unit](ItemNotFound)))
      }
    }

    describe("if already registered") {
      it("should not be allowed") {
        val kindId: CatalogItemId = catalogItemId.getOrElse(fail())
        val server: ActorRef[ItemServerCommand] = itemServer.getOrElse(fail())
        server ! AddItem(itemId, kindId, store, itemResponseProbe.ref)
        checkInPlaceItem()
        server ! AddItem(itemId, kindId, store, itemResponseProbe.ref)
        itemResponseProbe.expectMessage(
          10.seconds,
          ItemResponse(Left[ValidationError, Item](ItemAlreadyPresent))
        )
      }
    }
  }
}
