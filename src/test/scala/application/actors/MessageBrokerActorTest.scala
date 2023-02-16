/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.*

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.MapHasAsJava

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import com.dimafeng.testcontainers.GenericContainer
import com.dimafeng.testcontainers.GenericContainer.DockerImage
import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.lifecycle.and
import com.dimafeng.testcontainers.scalatest.TestContainersForAll
import com.rabbitmq.client.*
import com.typesafe.config.*
import eu.timepit.refined.auto.given
import org.scalatest.BeforeAndAfterAll
import org.scalatest.EitherValues.*
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.utility.DockerImageName
import spray.json.enrichAny
import spray.json.enrichString

import application.actors.command.{MessageBrokerCommand, RootCommand}
import application.actors.command.MessageBrokerCommand.{CatalogItemLifted, CatalogItemPutInPlace}
import application.actors.command.RootCommand.Startup
import application.routes.entities.Response.EmptyResponse
import application.Serializers.given
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity}
import items.catalogitem.Repository as CatalogItemRepository
import items.catalogitem.domainevents.{
  CatalogItemLifted as CatalogItemLiftedEvent,
  CatalogItemPutInPlace as CatalogItemPutInPlaceEvent
}
import items.catalogitem.entities.*
import items.catalogitem.valueobjects.*
import items.catalogitem.Repository.CatalogItemNotFound
import items.item.Repository as ItemRepository
import items.item.domainevents.{ItemAddedToCart as ItemAddedToCartEvent, ItemReturned as ItemReturnedEvent}
import items.item.entities.{InPlaceItem, Item}
import items.item.valueobjects.{Customer, ItemId}
import items.RepositoryOperationFailed
import items.itemcategory.valueobjects.ItemCategoryId

class MessageBrokerActorTest extends AnyFunSpec with TestContainersForAll with BeforeAndAfterAll {

  override type Containers = PostgreSQLContainer and GenericContainer

  private val timeout: FiniteDuration = 300.seconds

  override def startContainers(): Containers = {
    val postgreSQLContainer: PostgreSQLContainer = PostgreSQLContainer
      .Def(
        dockerImageName = DockerImageName.parse("postgres:15.1"),
        databaseName = "items",
        username = "test",
        password = "test",
        commonJdbcParams = CommonParams(timeout, timeout, Some("items.sql"))
      )
      .createContainer()
    val rabbitMQContainer: GenericContainer = GenericContainer(
      dockerImage = DockerImage(Left[String, Future[String]]("rabbitmq:3.11.7")),
      exposedPorts = Seq(5672),
      env = Map(
        "RABBITMQ_DEFAULT_USER" -> "test",
        "RABBITMQ_DEFAULT_PASS" -> "test"
      ),
      waitStrategy = LogMessageWaitStrategy().withRegEx("^.*?Server startup complete.*?$")
    )
    postgreSQLContainer.start()
    rabbitMQContainer.start()
    postgreSQLContainer and rabbitMQContainer
  }

  private val testKit: ActorTestKit = ActorTestKit()
  private val rootActorProbe: TestProbe[RootCommand] = testKit.createTestProbe[RootCommand]()
  private val emptyResponseProbe: TestProbe[EmptyResponse] = testKit.createTestProbe[EmptyResponse]()
  private val storesQueue: BlockingQueue[Map[String, String]] = LinkedBlockingDeque()
  private val shoppingQueue: BlockingQueue[Map[String, String]] = LinkedBlockingDeque()

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  private val correlationId: String = UUID.randomUUID().toString

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var messageBroker: Option[ActorRef[MessageBrokerCommand]] = None

  private def forwardToQueue(queue: BlockingQueue[Map[String, String]]): DeliverCallback =
    (_: String, message: Delivery) =>
      queue.put(
        Map(
          "exchange" -> message.getEnvelope.getExchange,
          "routingKey" -> message.getEnvelope.getRoutingKey,
          "body" -> String(message.getBody, StandardCharsets.UTF_8),
          "contentType" -> message.getProperties.getContentType,
          "correlationId" -> message.getProperties.getCorrelationId
        )
      )

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var messageBrokerChannel: Option[Channel] = None

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var maybeCatalogItem: Option[InPlaceCatalogItem] = None

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var maybeItem: Option[InPlaceItem] = None

  private val itemCategoryId: ItemCategoryId = ItemCategoryId(1).getOrElse(fail())
  private val store: Store = Store(1).getOrElse(fail())
  private val price: Price = Price(Amount(12.99).getOrElse(fail()), Currency.EUR)
  private val customer: Customer = Customer("mario@mail.com").getOrElse(fail())

  override def afterContainersStart(containers: Containers): Unit = {
    val generalConfig: Config = ConfigFactory.load()
    val messageBrokerConfig: Config =
      generalConfig
        .getConfig("messageBroker")
        .withValue(
          "portNumber",
          ConfigValueFactory.fromAnyRef(containers.tail.container.getFirstMappedPort.intValue())
        )
    val repositoryConfig: Config =
      generalConfig
        .getConfig("repository")
        .withValue(
          "dataSource.portNumber",
          ConfigValueFactory.fromAnyRef(containers.head.container.getFirstMappedPort.intValue())
        )
    messageBroker = Some(
      testKit.spawn(
        MessageBrokerActor(
          rootActorProbe.ref,
          messageBrokerConfig,
          repositoryConfig
        )
      )
    )
    val factory: ConnectionFactory = ConnectionFactory()
    factory.setUsername(messageBrokerConfig.getString("username"))
    factory.setPassword(messageBrokerConfig.getString("password"))
    factory.setVirtualHost(messageBrokerConfig.getString("virtualHost"))
    factory.setHost(messageBrokerConfig.getString("hostName"))
    factory.setPort(messageBrokerConfig.getInt("portNumber"))
    val connection: Connection = factory.newConnection()
    val channel: Channel = connection.createChannel()
    val couples: Seq[(String, String)] = Seq(
      "shopping" -> "items",
      "carts" -> "items",
      "stores" -> "items"
    )
    val queueArgs: Map[String, String] = Map("x-dead-letter-exchange" -> "dead_letters")
    couples.flatMap(Seq(_, _)).distinct.foreach(e => channel.exchangeDeclare(e, BuiltinExchangeType.TOPIC, true))
    couples
      .flatMap((b1, b2) => Seq(b1 + "_" + b2, b2 + "_" + b1))
      .foreach(q => channel.queueDeclare(q, true, false, false, queueArgs.asJava))
    couples
      .flatMap((b1, b2) => Seq((b1, b1 + "_" + b2, b2), (b2, b2 + "_" + b1, b1)))
      .foreach((e, q, r) => channel.queueBind(q, e, r))
    channel.basicConsume("items_stores", true, forwardToQueue(storesQueue), (_: String) => {})
    channel.basicConsume("items_shopping", true, forwardToQueue(shoppingQueue), (_: String) => {})
    messageBrokerChannel = Some(channel)
    val catalogItemRepository: CatalogItemRepository = CatalogItemRepository(repositoryConfig)
    val catalogItem: InPlaceCatalogItem = catalogItemRepository.add(itemCategoryId, store, price).getOrElse(fail())
    maybeCatalogItem = Some(catalogItem)
    val itemRepository: ItemRepository = ItemRepository(repositoryConfig)
    val item: InPlaceItem = InPlaceItem(ItemId(1).getOrElse(fail()), catalogItem)
    itemRepository.add(item).getOrElse(fail())
    maybeItem = Some(item)
  }

  override def afterAll(): Unit = testKit.shutdownTestKit()

  describe("A message broker actor") {
    describe("after being created") {
      it("should notify its root actor about it") {
        rootActorProbe.expectMessage(10.seconds, Startup(true))
      }
    }

    describe("when receives a CatalogItem lifted event for an existing catalog item") {
      it("should update the database with the new catalog item state") {
        val channel: Channel = messageBrokerChannel.getOrElse(fail())
        val catalogItem: CatalogItem = maybeCatalogItem.getOrElse(fail())
        channel.basicPublish(
          "stores",
          "items",
          true,
          AMQP
            .BasicProperties
            .Builder()
            .contentType("application/json")
            .deliveryMode(2)
            .priority(0)
            .replyTo("stores")
            .correlationId(correlationId)
            .build(),
          CatalogItemLiftedEvent(catalogItem.id, catalogItem.store).toJson.compactPrint.getBytes(StandardCharsets.UTF_8)
        )
        val firstMessage: Map[String, String] = storesQueue.poll(10, TimeUnit.SECONDS)
        firstMessage("exchange") shouldBe "items"
        firstMessage("routingKey") shouldBe "stores"
        firstMessage("contentType") shouldBe "application/json"
        firstMessage("correlationId") shouldBe correlationId
        firstMessage("body").parseJson.convertTo[ResultResponseEntity[Unit]].result shouldBe ()
        channel.basicPublish(
          "stores",
          "items",
          true,
          AMQP
            .BasicProperties
            .Builder()
            .contentType("application/json")
            .deliveryMode(2)
            .priority(0)
            .replyTo("stores")
            .correlationId(correlationId)
            .build(),
          CatalogItemLiftedEvent(catalogItem.id, catalogItem.store).toJson.compactPrint.getBytes(StandardCharsets.UTF_8)
        )
        val secondMessage: Map[String, String] = storesQueue.poll(10, TimeUnit.SECONDS)
        secondMessage("exchange") shouldBe "items"
        secondMessage("routingKey") shouldBe "stores"
        secondMessage("contentType") shouldBe "application/json"
        secondMessage("correlationId") shouldBe correlationId
        secondMessage("body").parseJson.convertTo[ResultResponseEntity[Unit]].result shouldBe ()
      }
    }

    describe("when receives a CatalogItem lifted event for a non-existing id") {
      it("should not be allowed") {
        val channel: Channel = messageBrokerChannel.getOrElse(fail())
        val catalogItem: CatalogItem = maybeCatalogItem.getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(999).getOrElse(fail())
        channel.basicPublish(
          "stores",
          "items",
          true,
          AMQP
            .BasicProperties
            .Builder()
            .contentType("application/json")
            .deliveryMode(2)
            .priority(0)
            .replyTo("stores")
            .correlationId(correlationId)
            .build(),
          CatalogItemLiftedEvent(catalogItemId, catalogItem.store).toJson.compactPrint.getBytes(StandardCharsets.UTF_8)
        )
        val message: Map[String, String] = storesQueue.poll(10, TimeUnit.SECONDS)
        message("exchange") shouldBe "items"
        message("routingKey") shouldBe "stores"
        message("contentType") shouldBe "application/json"
        message("correlationId") shouldBe correlationId
        message("body").parseJson.convertTo[ErrorResponseEntity].error shouldBe CatalogItemNotFound
      }
    }

    describe("when receives a CatalogItem lifted event for a non-existing store") {
      it("should not be allowed") {
        val channel: Channel = messageBrokerChannel.getOrElse(fail())
        val catalogItem: CatalogItem = maybeCatalogItem.getOrElse(fail())
        val store: Store = Store(999).getOrElse(fail())
        channel.basicPublish(
          "stores",
          "items",
          true,
          AMQP
            .BasicProperties
            .Builder()
            .contentType("application/json")
            .deliveryMode(2)
            .priority(0)
            .replyTo("stores")
            .correlationId(correlationId)
            .build(),
          CatalogItemLiftedEvent(catalogItem.id, store).toJson.compactPrint.getBytes(StandardCharsets.UTF_8)
        )
        val message: Map[String, String] = storesQueue.poll(10, TimeUnit.SECONDS)
        message("exchange") shouldBe "items"
        message("routingKey") shouldBe "stores"
        message("contentType") shouldBe "application/json"
        message("correlationId") shouldBe correlationId
        message("body").parseJson.convertTo[ErrorResponseEntity].error shouldBe CatalogItemNotFound
      }
    }

    describe("when receives a CatalogItem put in place event for an existing catalog item") {
      it("should update the database with the new catalog item state") {
        val actor: ActorRef[MessageBrokerCommand] = messageBroker.getOrElse(fail())
        val catalogItem: CatalogItem = maybeCatalogItem.getOrElse(fail())
        actor ! CatalogItemPutInPlace(CatalogItemPutInPlaceEvent(catalogItem.id, catalogItem.store), emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
        actor ! CatalogItemPutInPlace(CatalogItemPutInPlaceEvent(catalogItem.id, catalogItem.store), emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Right[ValidationError, Unit](())))
      }
    }

    describe("when receives a CatalogItem put in place event for a non-existing id") {
      it("should not be allowed") {
        val actor: ActorRef[MessageBrokerCommand] = messageBroker.getOrElse(fail())
        val catalogItem: CatalogItem = maybeCatalogItem.getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(999).getOrElse(fail())
        actor ! CatalogItemPutInPlace(CatalogItemPutInPlaceEvent(catalogItemId, catalogItem.store), emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Left[ValidationError, Unit](CatalogItemNotFound)))
      }
    }

    describe("when is called with a non-existing store") {
      it("should not be allowed") {
        val actor: ActorRef[MessageBrokerCommand] = messageBroker.getOrElse(fail())
        val catalogItem: CatalogItem = maybeCatalogItem.getOrElse(fail())
        val store: Store = Store(999).getOrElse(fail())
        actor ! CatalogItemPutInPlace(CatalogItemPutInPlaceEvent(catalogItem.id, store), emptyResponseProbe.ref)
        emptyResponseProbe.expectMessage(10.seconds, EmptyResponse(Left[ValidationError, Unit](CatalogItemNotFound)))
      }
    }
  }

  describe("when receives an Item added to cart event for an existing item") {
    it("should update the database with the new item state") {
      val channel: Channel = messageBrokerChannel.getOrElse(fail())
      val item: Item = maybeItem.getOrElse(fail())
      channel.basicPublish(
        "shopping",
        "items",
        true,
        AMQP
          .BasicProperties
          .Builder()
          .contentType("application/json")
          .deliveryMode(2)
          .priority(0)
          .replyTo("shopping")
          .correlationId(correlationId)
          .build(),
        ItemAddedToCartEvent(item.kind.id, item.kind.store, item.id, customer)
          .toJson
          .compactPrint
          .getBytes(StandardCharsets.UTF_8)
      )
      val message: Map[String, String] = shoppingQueue.poll(10, TimeUnit.SECONDS)
      message("exchange") shouldBe "items"
      message("routingKey") shouldBe "shopping"
      message("contentType") shouldBe "application/json"
      message("correlationId") shouldBe correlationId
      message("body").parseJson.convertTo[ResultResponseEntity[Unit]].result shouldBe ()
    }
  }

  describe("when receives a Item added to cart event for a non-existing catalog item") {
    it("should not be allowed") {
      val channel: Channel = messageBrokerChannel.getOrElse(fail())
      val item: Item = maybeItem.getOrElse(fail())
      val catalogItemId: CatalogItemId = CatalogItemId(999).getOrElse(fail())
      channel.basicPublish(
        "shopping",
        "items",
        true,
        AMQP
          .BasicProperties
          .Builder()
          .contentType("application/json")
          .deliveryMode(2)
          .priority(0)
          .replyTo("shopping")
          .correlationId(correlationId)
          .build(),
        ItemAddedToCartEvent(catalogItemId, item.kind.store, item.id, customer)
          .toJson
          .compactPrint
          .getBytes(StandardCharsets.UTF_8)
      )
      val message: Map[String, String] = shoppingQueue.poll(10, TimeUnit.SECONDS)
      message("exchange") shouldBe "items"
      message("routingKey") shouldBe "shopping"
      message("contentType") shouldBe "application/json"
      message("correlationId") shouldBe correlationId
      message("body").parseJson.convertTo[ErrorResponseEntity].error shouldBe CatalogItemNotFound
    }
  }

  describe("when receives an Item added to cart event for a non-existing id") {
    it("should not be allowed") {
      val channel: Channel = messageBrokerChannel.getOrElse(fail())
      val item: Item = maybeItem.getOrElse(fail())
      val itemId: ItemId = ItemId(999).getOrElse(fail())
      channel.basicPublish(
        "shopping",
        "items",
        true,
        AMQP
          .BasicProperties
          .Builder()
          .contentType("application/json")
          .deliveryMode(2)
          .priority(0)
          .replyTo("shopping")
          .correlationId(correlationId)
          .build(),
        ItemAddedToCartEvent(item.kind.id, item.kind.store, itemId, customer).toJson.compactPrint.getBytes(StandardCharsets.UTF_8)
      )
      val message: Map[String, String] = shoppingQueue.poll(10, TimeUnit.SECONDS)
      message("exchange") shouldBe "items"
      message("routingKey") shouldBe "shopping"
      message("contentType") shouldBe "application/json"
      message("correlationId") shouldBe correlationId
      message("body").parseJson.convertTo[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
    }
  }

  describe("when receives an Item returned event for an existing item") {
    it("should update the database with the new item state") {
      val channel: Channel = messageBrokerChannel.getOrElse(fail())
      val item: Item = maybeItem.getOrElse(fail())
      channel.basicPublish(
        "stores",
        "items",
        true,
        AMQP
          .BasicProperties
          .Builder()
          .contentType("application/json")
          .deliveryMode(2)
          .priority(0)
          .replyTo("stores")
          .correlationId(correlationId)
          .build(),
        ItemReturnedEvent(item.kind.id, item.kind.store, item.id)
          .toJson
          .compactPrint
          .getBytes(StandardCharsets.UTF_8)
      )
      val message: Map[String, String] = storesQueue.poll(10, TimeUnit.SECONDS)
      message("exchange") shouldBe "items"
      message("routingKey") shouldBe "stores"
      message("contentType") shouldBe "application/json"
      message("correlationId") shouldBe correlationId
      message("body").parseJson.convertTo[ResultResponseEntity[Unit]].result shouldBe ()
    }
  }

  describe("when receives a Item returned event for a non-existing catalog item") {
    it("should not be allowed") {
      val channel: Channel = messageBrokerChannel.getOrElse(fail())
      val item: Item = maybeItem.getOrElse(fail())
      val catalogItemId: CatalogItemId = CatalogItemId(999).getOrElse(fail())
      channel.basicPublish(
        "stores",
        "items",
        true,
        AMQP
          .BasicProperties
          .Builder()
          .contentType("application/json")
          .deliveryMode(2)
          .priority(0)
          .replyTo("stores")
          .correlationId(correlationId)
          .build(),
        ItemReturnedEvent(catalogItemId, item.kind.store, item.id)
          .toJson
          .compactPrint
          .getBytes(StandardCharsets.UTF_8)
      )
      val message: Map[String, String] = storesQueue.poll(10, TimeUnit.SECONDS)
      message("exchange") shouldBe "items"
      message("routingKey") shouldBe "stores"
      message("contentType") shouldBe "application/json"
      message("correlationId") shouldBe correlationId
      message("body").parseJson.convertTo[ErrorResponseEntity].error shouldBe CatalogItemNotFound
    }
  }

  describe("when receives an Item returned event for a non-existing id") {
    it("should not be allowed") {
      val channel: Channel = messageBrokerChannel.getOrElse(fail())
      val item: Item = maybeItem.getOrElse(fail())
      val itemId: ItemId = ItemId(999).getOrElse(fail())
      channel.basicPublish(
        "stores",
        "items",
        true,
        AMQP
          .BasicProperties
          .Builder()
          .contentType("application/json")
          .deliveryMode(2)
          .priority(0)
          .replyTo("stores")
          .correlationId(correlationId)
          .build(),
        ItemReturnedEvent(item.kind.id, item.kind.store, itemId).toJson.compactPrint.getBytes(StandardCharsets.UTF_8)
      )
      val message: Map[String, String] = storesQueue.poll(10, TimeUnit.SECONDS)
      message("exchange") shouldBe "items"
      message("routingKey") shouldBe "stores"
      message("contentType") shouldBe "application/json"
      message("correlationId") shouldBe correlationId
      message("body").parseJson.convertTo[ErrorResponseEntity].error shouldBe RepositoryOperationFailed
    }
  }
}
