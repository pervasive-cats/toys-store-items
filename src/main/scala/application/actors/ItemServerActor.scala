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
import scala.util.Failure
import scala.util.Success

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.Config

import application.actors.command.{ItemServerCommand, RootCommand}
import application.actors.command.ItemServerCommand.*
import application.actors.command.RootCommand.Startup
import application.routes.entities.Response.*
import application.RequestProcessingFailed
import items.catalogitem.Repository as CatalogItemRepository
import items.item.Repository as ItemRepository
import items.item.entities.{InPlaceItem, Item}

object ItemServerActor {

  def apply(root: ActorRef[RootCommand], repositoryConfig: Config): Behavior[ItemServerCommand] = Behaviors.setup { ctx =>
    given catalogItemRepository: CatalogItemRepository = CatalogItemRepository(repositoryConfig)
    val itemRepository: ItemRepository = ItemRepository(repositoryConfig)
    given ExecutionContext = ExecutionContext.fromExecutor(ForkJoinPool.commonPool())
    root ! Startup(success = true)
    Behaviors.receiveMessage {
      case ShowItem(id, kind, store, replyTo) =>
        Future(itemRepository.findById(id, kind, store)).onComplete {
          case Failure(_) => replyTo ! ItemResponse(Left[ValidationError, Item](RequestProcessingFailed))
          case Success(value) => replyTo ! ItemResponse(value)
        }(ctx.executionContext)
        Behaviors.same[ItemServerCommand]
      case ShowAllReturnedItems(replyTo) =>
        Future(itemRepository.findAllReturned()).onComplete {
          case Failure(_) =>
            replyTo ! ReturnedItemSetResponse(
              Left[ValidationError, Set[Validated[Item]]](RequestProcessingFailed)
            )
          case Success(value) => replyTo ! ReturnedItemSetResponse(value.map(_.map(_.map(l => l: Item))))
        }(ctx.executionContext)
        Behaviors.same[ItemServerCommand]
      case AddItem(id, kind, store, replyTo) =>
        Future(for {
          k <- catalogItemRepository.findById(kind, store)
          i = InPlaceItem(id, k)
          _ <- itemRepository.add(i)
        } yield i).onComplete {
          case Failure(_) => replyTo ! ItemResponse(Left[ValidationError, Item](RequestProcessingFailed))
          case Success(value) => replyTo ! ItemResponse(value)
        }(ctx.executionContext)
        Behaviors.same[ItemServerCommand]
      case RemoveItem(id, kind, store, replyTo) =>
        Future(for {
          i <- itemRepository.findById(id, kind, store)
          _ <- itemRepository.remove(i)
        } yield ()).onComplete {
          case Failure(_) => replyTo ! EmptyResponse(Left[ValidationError, Unit](RequestProcessingFailed))
          case Success(value) => replyTo ! EmptyResponse(value)
        }(ctx.executionContext)
        Behaviors.same[ItemServerCommand]
    }
  }
}
