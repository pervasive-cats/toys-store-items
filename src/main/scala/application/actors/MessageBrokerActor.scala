/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import java.nio.charset.StandardCharsets
import java.util.concurrent.{Executors, ForkJoinPool}
import javax.sql.DataSource
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters.MapHasAsJava
import scala.util.*
import akka.actor.typed.*
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import com.rabbitmq.client.*
import com.typesafe.config.Config
import spray.json.DefaultJsonProtocol.StringJsonFormat
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.enrichAny
import spray.json.enrichString
import application.actors.command.{MessageBrokerCommand, RootCommand}
import application.actors.command.RootCommand.Startup
import application.Serializers.given
import application.actors.command.MessageBrokerCommand.*
import application.routes.entities.Entity
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity}
import application.routes.entities.Response.EmptyResponse
import application.RequestProcessingFailed
import items.catalogitem.{CatalogItemStateHandlers, Repository as CatalogItemRepository}
import items.catalogitem.domainevents.{CatalogItemLifted as CatalogItemLiftedEvent, CatalogItemPutInPlace as CatalogItemPutInPlaceEvent}
import items.item.Repository as ItemRepository
import items.item.domainevents.{ItemAddedToCart as ItemAddedToCartEvent, ItemReturned as ItemReturnedEvent}
import items.item.services.ItemStateHandlers
import AnyOps.===

object MessageBrokerActor {

  private def publish[A <: Entity: JsonFormat](channel: Channel, response: A, replyTo: String, correlationId: String): Unit =
    channel.basicPublish(
      "items",
      replyTo,
      AMQP
        .BasicProperties
        .Builder()
        .contentType("application/json")
        .deliveryMode(2)
        .priority(0)
        .correlationId(correlationId)
        .build(),
      response.toJson.compactPrint.getBytes(StandardCharsets.UTF_8)
    )

  private def publishValidated[A: JsonFormat](
    channel: Channel,
    value: Validated[A],
    replyTo: String,
    correlationId: String
  ): Unit =
    value.fold(
      t => publish(channel, ErrorResponseEntity(t), replyTo, correlationId),
      _ => publish(channel, ResultResponseEntity(()), replyTo, correlationId)
    )

  private def consume[A <: MessageBrokerCommand](
    queue: String,
    channel: Channel,
    events: Map[String, (JsObject, String, String) => A],
    ctx: ActorContext[MessageBrokerCommand]
  ): Unit =
    channel.basicConsume(
      queue,
      true,
      (_: String, message: Delivery) => {
        val body: String = String(message.getBody, StandardCharsets.UTF_8)
        val json: JsObject = body.parseJson.convertTo[ResultResponseEntity[JsValue]].result.asJsObject
        events
          .toSeq
          .find((eventName, _) =>
            json.getFields("type") match {
              case Seq(JsString(e)) => eventName === e
              case _ => false
            }
          )
          .map(_._2)
          .fold {
            ctx.system.deadLetters[String] ! body
            channel.basicReject(message.getEnvelope.getDeliveryTag, false)
          }(b =>
            ctx.self ! b.apply(
              json,
              message.getProperties.getReplyTo,
              message.getProperties.getCorrelationId
            )
          )
      },
      (_: String) => {}
    )

  def apply(root: ActorRef[RootCommand], messageBrokerConfig: Config, dataSource: DataSource): Behavior[MessageBrokerCommand] =
    Behaviors.setup[MessageBrokerCommand] { ctx =>
      Try {
        val factory: ConnectionFactory = ConnectionFactory()
        factory.setUsername(messageBrokerConfig.getString("username"))
        factory.setPassword(messageBrokerConfig.getString("password"))
        factory.setVirtualHost(messageBrokerConfig.getString("virtualHost"))
        factory.setHost(messageBrokerConfig.getString("hostName"))
        factory.setPort(messageBrokerConfig.getInt("portNumber"))
        factory.newConnection()
      }.flatMap { c =>
        val channel: Channel = c.createChannel()
        channel.addReturnListener((r: Return) => {
          ctx.system.deadLetters[String] ! String(r.getBody, StandardCharsets.UTF_8)
          channel.basicPublish(
            "dead_letters",
            "dead_letters",
            AMQP
              .BasicProperties
              .Builder()
              .contentType("application/json")
              .deliveryMode(2)
              .priority(0)
              .build(),
            r.getBody
          )
        })
        Try {
          channel.exchangeDeclare("dead_letters", BuiltinExchangeType.FANOUT, true)
          channel.queueDeclare("dead_letters", true, false, false, Map.empty.asJava)
          channel.queueBind("dead_letters", "dead_letters", "")
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
          consume(
            "shopping_items",
            channel,
            Map(
              "CatalogItemLifted" -> ((j, r, c) => CatalogItemLifted(summon[JsonFormat[CatalogItemLiftedEvent]].read(j), r, c)),
              "ItemAddedToCart" -> ((j, r, c) => ItemAddedToCart(summon[JsonFormat[ItemAddedToCartEvent]].read(j), r, c))
            ),
            ctx
          )
          consume(
            "stores_items",
            channel,
            Map(
              "CatalogItemLifted" -> ((j, r, c) => CatalogItemLifted(summon[JsonFormat[CatalogItemLiftedEvent]].read(j), r, c)),
              "ItemReturned" -> ((j, r, c) => ItemReturned(summon[JsonFormat[ItemReturnedEvent]].read(j), r, c))
            ),
            ctx
          )
          consume(
            "carts_items",
            channel,
            Map("ItemAddedToCart" -> ((j, r, c) => ItemAddedToCart(summon[JsonFormat[ItemAddedToCartEvent]].read(j), r, c))),
            ctx
          )
          (c, channel)
        }
      }.map { (co, ch) =>
        root ! Startup(true)
        given CatalogItemRepository = CatalogItemRepository(dataSource)
        given ItemRepository = ItemRepository(dataSource)
        given ExecutionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
        Behaviors
          .receiveMessage[MessageBrokerCommand] {
            case CatalogItemLifted(event, replyTo, correlationId) =>
              Future(
                CatalogItemStateHandlers.onCatalogItemLifted(event)
              ).onComplete {
                case Failure(_) => publish(ch, ErrorResponseEntity(RequestProcessingFailed), replyTo, correlationId)
                case Success(value) => publishValidated(ch, value, replyTo, correlationId)
              }(ctx.executionContext)
              Behaviors.same[MessageBrokerCommand]
            case CatalogItemPutInPlace(event, replyTo) =>
              Future(CatalogItemStateHandlers.onCatalogItemPutInPlace(event)).onComplete {
                case Failure(_) => replyTo ! EmptyResponse(Left[ValidationError, Unit](RequestProcessingFailed))
                case Success(value) => replyTo ! EmptyResponse(value)
              }(ctx.executionContext)
              Behaviors.same[MessageBrokerCommand]
            case ItemPutInPlace(event, replyTo) =>
              Future(ItemStateHandlers.onItemPutInPlace(event)).onComplete {
                case Failure(_) => replyTo ! EmptyResponse(Left[ValidationError, Unit](RequestProcessingFailed))
                case Success(value) => replyTo ! EmptyResponse(value)
              }(ctx.executionContext)
              Behaviors.same[MessageBrokerCommand]
            case ItemAddedToCart(event, replyTo, correlationId) =>
              Future(ItemStateHandlers.onItemAddedToCart(event)).onComplete {
                case Failure(_) => publish(ch, ErrorResponseEntity(RequestProcessingFailed), replyTo, correlationId)
                case Success(value) => publishValidated(ch, value, replyTo, correlationId)
              }(ctx.executionContext)
              Behaviors.same[MessageBrokerCommand]
            case ItemReturned(event, replyTo, correlationId) =>
              Future(ItemStateHandlers.onItemReturned(event)).onComplete {
                case Failure(_) => publish(ch, ErrorResponseEntity(RequestProcessingFailed), replyTo, correlationId)
                case Success(value) => publishValidated(ch, value, replyTo, correlationId)
              }(ctx.executionContext)
              Behaviors.same[MessageBrokerCommand]
          }
          .receiveSignal {
            case (_, PostStop) =>
              ch.close()
              co.close()
              Behaviors.same[MessageBrokerCommand]
          }
      }.getOrElse {
        root ! Startup(false)
        Behaviors.stopped[MessageBrokerCommand]
      }
    }
}
