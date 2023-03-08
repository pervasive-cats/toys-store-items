/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.actors

import java.util.concurrent.ForkJoinPool
import javax.sql.DataSource

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.config.Config

import application.actors.command.{CatalogItemServerCommand, RootCommand}
import application.actors.command.CatalogItemServerCommand.*
import application.actors.command.RootCommand.Startup
import application.routes.entities.Response.*
import application.RequestProcessingFailed
import items.catalogitem.Repository as CatalogItemRepository
import items.catalogitem.entities.*
import items.catalogitem.entities.CatalogItemOps.updated

object CatalogItemServerActor {

  def apply(root: ActorRef[RootCommand], dataSource: DataSource): Behavior[CatalogItemServerCommand] = Behaviors.setup { ctx =>
    val catalogItemRepository: CatalogItemRepository = CatalogItemRepository(dataSource)
    given ExecutionContext = ExecutionContext.fromExecutor(ForkJoinPool.commonPool())
    root ! Startup(success = true)
    Behaviors.receiveMessage {
      case ShowCatalogItem(id, store, replyTo) =>
        Future(catalogItemRepository.findById(id, store)).onComplete {
          case Failure(_) => replyTo ! CatalogItemResponse(Left[ValidationError, CatalogItem](RequestProcessingFailed))
          case Success(value) => replyTo ! CatalogItemResponse(value)
        }(ctx.executionContext)
        Behaviors.same[CatalogItemServerCommand]
      case ShowAllLiftedCatalogItems(replyTo) =>
        Future(catalogItemRepository.findAllLifted()).onComplete {
          case Failure(_) =>
            replyTo ! LiftedCatalogItemSetResponse(
              Left[ValidationError, Set[Validated[CatalogItem]]](RequestProcessingFailed)
            )
          case Success(value) => replyTo ! LiftedCatalogItemSetResponse(value.map(_.map(_.map(l => l: CatalogItem))))
        }(ctx.executionContext)
        Behaviors.same[CatalogItemServerCommand]
      case AddCatalogItem(id, store, price, replyTo) =>
        Future(catalogItemRepository.add(id, store, price)).onComplete {
          case Failure(_) => replyTo ! CatalogItemResponse(Left[ValidationError, CatalogItem](RequestProcessingFailed))
          case Success(value) => replyTo ! CatalogItemResponse(value)
        }(ctx.executionContext)
        Behaviors.same[CatalogItemServerCommand]
      case UpdateCatalogItem(id, store, price, replyTo) =>
        Future(for {
          c <- catalogItemRepository.findById(id, store)
          n <- c match {
            case i: InPlaceCatalogItem =>
              catalogItemRepository.update(i, None, price).map(_ => i.updated(price))
            case i: LiftedCatalogItem =>
              catalogItemRepository.update(i, Some(i.count), price).map(_ => i.updated(price))
          }
        } yield n).onComplete {
          case Failure(_) => replyTo ! CatalogItemResponse(Left[ValidationError, CatalogItem](RequestProcessingFailed))
          case Success(value) => replyTo ! CatalogItemResponse(value)
        }(ctx.executionContext)
        Behaviors.same[CatalogItemServerCommand]
      case RemoveCatalogItem(id, store, replyTo) =>
        Future(for {
          c <- catalogItemRepository.findById(id, store)
          _ <- catalogItemRepository.remove(c)
        } yield ()).onComplete {
          case Failure(_) => replyTo ! EmptyResponse(Left[ValidationError, Unit](RequestProcessingFailed))
          case Success(value) => replyTo ! EmptyResponse(value)
        }(ctx.executionContext)
        Behaviors.same[CatalogItemServerCommand]
    }
  }
}
