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

import application.actors.command.{ItemCategoryServerCommand, RootCommand}
import application.actors.command.ItemCategoryServerCommand.*
import application.actors.command.RootCommand.Startup
import application.routes.entities.Response.{EmptyResponse, ItemCategoryResponse}
import items.itemcategory.entities.ItemCategory
import items.itemcategory.entities.ItemCategoryOps.updated
import items.itemcategory.valueobjects.*
import items.itemcategory.Repository.ItemCategoryNotFound

class ItemCategoryServerActorTest extends AnyFunSpec with TestContainerForAll with BeforeAndAfterAll {

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
  private val itemCategoryResponseProbe: TestProbe[ItemCategoryResponse] = testKit.createTestProbe[ItemCategoryResponse]()
  private val emptyResponseProbe: TestProbe[EmptyResponse] = testKit.createTestProbe[EmptyResponse]()

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var itemCategoryServer: Option[ActorRef[ItemCategoryServerCommand]] = None

  override def afterContainersStart(containers: Containers): Unit =
    itemCategoryServer = Some(
      testKit.spawn(
        ItemCategoryServerActor(
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

  private val itemCategoryId: ItemCategoryId = ItemCategoryId(1000).getOrElse(fail())
  private val name: Name = Name("Lego Bat-mobile").getOrElse(fail())
  private val description: Description = Description("Long long long description about a product").getOrElse(fail())

  private def checkItemCategory(name: Name, description: Description): ItemCategoryId =
    itemCategoryResponseProbe.expectMessageType[ItemCategoryResponse](10.seconds).result match {
      case Left(_) => fail()
      case Right(i) =>
        i.name shouldBe name
        i.description shouldBe description
        i.id
    }

  describe("An item category server actor") {
    describe("when first started up") {
      it("should notify the root actor of its start") {
        rootActorProbe.expectMessage(10.seconds, Startup(true))
      }
    }
  }

  describe("An item category") {
    describe("after being added") {
      it("should be present in the database") {
        val server: ActorRef[ItemCategoryServerCommand] = itemCategoryServer.getOrElse(fail())
        server ! AddItemCategory(name, description, itemCategoryResponseProbe.ref)
        val itemCategoryId: ItemCategoryId = checkItemCategory(name, description)
        server ! ShowItemCategory(itemCategoryId, itemCategoryResponseProbe.ref)
        checkItemCategory(name, description)
        server ! RemoveItemCategory(itemCategoryId, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("after being added and while trying to find it with the wrong id") {
      it("should not be present in the database") {
        val server: ActorRef[ItemCategoryServerCommand] = itemCategoryServer.getOrElse(fail())
        server ! AddItemCategory(name, description, itemCategoryResponseProbe.ref)
        val itemCategoryId: ItemCategoryId = checkItemCategory(name, description)
        val wrongItemCategoryId: ItemCategoryId = ItemCategoryId(9000).getOrElse(fail())
        assume(itemCategoryId !== wrongItemCategoryId)
        server ! ShowItemCategory(wrongItemCategoryId, itemCategoryResponseProbe.ref)
        itemCategoryResponseProbe.expectMessage(
          10.seconds,
          ItemCategoryResponse(Left[ValidationError, ItemCategory](ItemCategoryNotFound))
        )
        server ! RemoveItemCategory(itemCategoryId, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("after being added and then removed") {
      it("should not be present in the database") {
        val server: ActorRef[ItemCategoryServerCommand] = itemCategoryServer.getOrElse(fail())
        server ! AddItemCategory(name, description, itemCategoryResponseProbe.ref)
        val itemCategoryId: ItemCategoryId = checkItemCategory(name, description)
        server ! RemoveItemCategory(itemCategoryId, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
        server ! ShowItemCategory(itemCategoryId, itemCategoryResponseProbe.ref)
        itemCategoryResponseProbe.expectMessage(
          10.seconds,
          ItemCategoryResponse(Left[ValidationError, ItemCategory](ItemCategoryNotFound))
        )
      }
    }

    describe("after being added and while trying to get deleted from the database with the wrong id") {
      it("should not be allowed") {
        val server: ActorRef[ItemCategoryServerCommand] = itemCategoryServer.getOrElse(fail())
        server ! AddItemCategory(name, description, itemCategoryResponseProbe.ref)
        val itemCategoryId: ItemCategoryId = checkItemCategory(name, description)
        val wrongItemCategoryId: ItemCategoryId = ItemCategoryId(9000).getOrElse(fail())
        assume(itemCategoryId !== wrongItemCategoryId)
        server ! RemoveItemCategory(wrongItemCategoryId, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Left[ValidationError, Unit](ItemCategoryNotFound)))
        server ! RemoveItemCategory(itemCategoryId, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("after being added and then its data gets updated") {
      it("should show the update") {
        val server: ActorRef[ItemCategoryServerCommand] = itemCategoryServer.getOrElse(fail())
        val newName: Name = Name("Lego Bat-mobile legacy edition").getOrElse(fail())
        val newDescription: Description = Description("Another, different description from the previous one").getOrElse(fail())
        server ! AddItemCategory(name, description, itemCategoryResponseProbe.ref)
        val itemCategoryId: ItemCategoryId = checkItemCategory(name, description)
        server ! UpdateItemCategory(
          itemCategoryId,
          newName,
          newDescription,
          itemCategoryResponseProbe.ref
        )
        val newItemCategoryId: ItemCategoryId = checkItemCategory(newName, newDescription)
        newItemCategoryId shouldBe itemCategoryId
        server ! ShowItemCategory(itemCategoryId, itemCategoryResponseProbe.ref)
        checkItemCategory(newName, newDescription)
        server ! RemoveItemCategory(newItemCategoryId, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("when their data gets updated but they were never registered in the first place") {
      it("should not be allowed") {
        val server: ActorRef[ItemCategoryServerCommand] = itemCategoryServer.getOrElse(fail())
        val newName: Name = Name("Lego Bat-mobile legacy edition").getOrElse(fail())
        val newDescription: Description = Description("Another, different description from the previous one").getOrElse(fail())
        server ! UpdateItemCategory(
          itemCategoryId,
          newName,
          newDescription,
          itemCategoryResponseProbe.ref
        )
        itemCategoryResponseProbe.expectMessage(
          10.seconds,
          ItemCategoryResponse(Left[ValidationError, ItemCategory](ItemCategoryNotFound))
        )
      }
    }

    describe("if never registered") {
      it("should not be present") {
        val server: ActorRef[ItemCategoryServerCommand] = itemCategoryServer.getOrElse(fail())
        server ! ShowItemCategory(itemCategoryId, itemCategoryResponseProbe.ref)
        itemCategoryResponseProbe.expectMessage(
          10.seconds,
          ItemCategoryResponse(Left[ValidationError, ItemCategory](ItemCategoryNotFound))
        )
        server ! RemoveItemCategory(itemCategoryId, emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Left[ValidationError, Unit](ItemCategoryNotFound)))
      }
    }

    describe("if already registered") {
      it("should allow a new registration") {
        val server: ActorRef[ItemCategoryServerCommand] = itemCategoryServer.getOrElse(fail())
        server ! AddItemCategory(name, description, itemCategoryResponseProbe.ref)
        val itemCategoryId: ItemCategoryId = checkItemCategory(name, description)
        server ! AddItemCategory(name, description, itemCategoryResponseProbe.ref)
        val secondItemCategoryId: ItemCategoryId = checkItemCategory(name, description)
        itemCategoryId should not be secondItemCategoryId
      }
    }
  }
}
