/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import java.util.concurrent.ForkJoinPool

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import akka.actor.typed.*
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import com.typesafe.config.Config

import application.actors.command.*
import application.actors.command.RootCommand.Startup
import application.routes.Routes

object RootActor {

  def apply(config: Config): Behavior[RootCommand] =
    Behaviors.setup { ctx =>
      val messageBrokerActor: ActorRef[MessageBrokerCommand] = ctx.spawn(
        MessageBrokerActor(ctx.self, config.getConfig("messageBroker"), config.getConfig("repository")),
        name = "message_broker_actor"
      )
      Behaviors.receiveMessage {
        case Startup(true) =>
          val repositoryConfig: Config = config.getConfig("repository")
          awaitServers(
            messageBrokerActor,
            ctx.spawn(ItemCategoryServerActor(ctx.self, repositoryConfig), name = "customer_server"),
            ctx.spawn(CatalogItemServerActor(ctx.self, repositoryConfig), name = "store_manager_server"),
            ctx.spawn(ItemServerActor(ctx.self, repositoryConfig), name = "administration_server"),
            config.getConfig("server"),
            count = 0
          )
        case Startup(false) => Behaviors.stopped[RootCommand]
        case _ => Behaviors.unhandled[RootCommand]
      }
    }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def awaitServers(
    messageBrokerActor: ActorRef[MessageBrokerCommand],
    itemCategoryServer: ActorRef[ItemCategoryServerCommand],
    catalogItemServer: ActorRef[CatalogItemServerCommand],
    itemServer: ActorRef[ItemServerCommand],
    serverConfig: Config,
    count: Int
  ): Behavior[RootCommand] = Behaviors.receive { (ctx, msg) =>
    msg match {
      case Startup(true) if count < 2 =>
        awaitServers(messageBrokerActor, itemCategoryServer, catalogItemServer, itemServer, serverConfig, count + 1)
      case Startup(true) =>
        given ActorSystem[_] = ctx.system
        val httpServer: Future[Http.ServerBinding] =
          Http()
            .newServerAt(serverConfig.getString("hostName"), serverConfig.getInt("portNumber"))
            .bind(Routes(messageBrokerActor, itemCategoryServer, catalogItemServer, itemServer))
        Behaviors.receiveSignal {
          case (_, PostStop) =>
            given ExecutionContext = ExecutionContext.fromExecutor(ForkJoinPool.commonPool())
            httpServer.flatMap(_.unbind()).onComplete(_ => println("Server has stopped"))
            Behaviors.same[RootCommand]
        }
      case Startup(false) => Behaviors.stopped[RootCommand]
      case _ => Behaviors.unhandled[RootCommand]
    }
  }
}
