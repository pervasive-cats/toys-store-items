/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import application.actors.command.{MessageBrokerCommand, RootCommand}
import application.actors.command.RootCommand.Startup
import application.Serializers.given
import application.actors.command.MessageBrokerCommand.{CatalogItemLifted, CatalogItemPutInPlace}
import application.routes.entities.Entity
import application.routes.entities.Entity.{ErrorResponseEntity, ResultResponseEntity}
import application.routes.entities.Response.EmptyResponse
import application.RequestProcessingFailed
import items.catalogitem.{CatalogItemStateHandlers, Repository}
import items.catalogitem.domainevents.{CatalogItemLifted as CatalogItemLiftedEvent, CatalogItemPutInPlace as CatalogItemPutInPlaceEvent}

import akka.actor.typed.*
import akka.actor.typed.scaladsl.Behaviors
import com.rabbitmq.client.*
import com.typesafe.config.Config
import spray.json.{enrichAny, enrichString, JsObject, JsonFormat, JsString, JsValue}
import spray.json.DefaultJsonProtocol.StringJsonFormat

import java.nio.charset.StandardCharsets
import java.util.concurrent.ForkJoinPool
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.MapHasAsJava
import scala.util.*

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

  def apply(root: ActorRef[RootCommand], messageBrokerConfig: Config, repositoryConfig: Config): Behavior[MessageBrokerCommand] =
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
          couples
            .map(_ + "_" + _)
            .foreach(q =>
              channel.basicConsume(
                q,
                true,
                (_: String, message: Delivery) => {
                  val body: String = String(message.getBody, StandardCharsets.UTF_8)
                  val json: JsObject = body.parseJson.asJsObject
                  json.getFields("type") match {
                    case Seq(JsString("CatalogItemLifted")) =>
                      ctx.self ! CatalogItemLifted(
                        json.convertTo[CatalogItemLiftedEvent],
                        message.getProperties.getReplyTo,
                        message.getProperties.getCorrelationId
                      )
                    case _ =>
                      ctx.system.deadLetters[String] ! body
                      channel.basicReject(message.getEnvelope.getDeliveryTag, false)
                  }
                },
                (_: String) => {}
              )
            )
          (c, channel)
        }
      }.map { (co, ch) =>
        root ! Startup(true)
        given Repository = Repository(repositoryConfig)
        given ExecutionContext = ExecutionContext.fromExecutor(ForkJoinPool.commonPool())
        Behaviors
          .receiveMessage[MessageBrokerCommand] {
            case CatalogItemLifted(event, replyTo, correlationId) =>
              Future(
                CatalogItemStateHandlers.onCatalogItemLifted(event)
              ).onComplete {
                case Failure(_) => publish(ch, ErrorResponseEntity(RequestProcessingFailed), replyTo, correlationId)
                case Success(value) =>
                  value.fold(
                    t => publish(ch, ErrorResponseEntity(t), replyTo, correlationId),
                    _ => publish(ch, ResultResponseEntity(()), replyTo, correlationId)
                  )
              }(ctx.executionContext)
              Behaviors.same[MessageBrokerCommand]
            case CatalogItemPutInPlace(event, replyTo) =>
              Future(CatalogItemStateHandlers.onCatalogItemPutInPlace(event)).onComplete {
                case Failure(_) => replyTo ! EmptyResponse(Left[ValidationError, Unit](RequestProcessingFailed))
                case Success(value) => replyTo ! EmptyResponse(value)
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
