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

import application.actors.command.{ItemCategoryServerCommand, RootCommand}
import application.actors.command.ItemCategoryServerCommand.*
import application.routes.entities.Response.{EmptyResponse, ItemCategoryResponse}
import application.RequestProcessingFailed
import application.actors.command.RootCommand.Startup
import items.itemcategory.Repository as ItemCategoryRepository
import items.itemcategory.entities.ItemCategory
import items.itemcategory.valueobjects.Description
import items.itemcategory.entities.ItemCategoryOps.updated

object ItemCategoryServerActor {

  def apply(root: ActorRef[RootCommand], dataSource: DataSource): Behavior[ItemCategoryServerCommand] = Behaviors.setup { ctx =>
    val itemCategoryRepository: ItemCategoryRepository = ItemCategoryRepository(dataSource)
    given ExecutionContext = ExecutionContext.fromExecutor(ForkJoinPool.commonPool())
    root ! Startup(success = true)
    Behaviors.receiveMessage {
      case ShowItemCategory(itemCategoryId, replyTo) =>
        Future(itemCategoryRepository.findById(itemCategoryId)).onComplete {
          case Failure(_) => replyTo ! ItemCategoryResponse(Left[ValidationError, ItemCategory](RequestProcessingFailed))
          case Success(value) => replyTo ! ItemCategoryResponse(value)
        }(ctx.executionContext)
        Behaviors.same[ItemCategoryServerCommand]
      case AddItemCategory(name, description, replyTo) =>
        Future(itemCategoryRepository.add(name, description)).onComplete {
          case Failure(_) => replyTo ! ItemCategoryResponse(Left[ValidationError, ItemCategory](RequestProcessingFailed))
          case Success(value) => replyTo ! ItemCategoryResponse(value)
        }(ctx.executionContext)
        Behaviors.same[ItemCategoryServerCommand]
      case UpdateItemCategory(id, newName, newDescription, replyTo) =>
        Future(for {
          i <- itemCategoryRepository.findById(id)
          _ <- itemCategoryRepository.update(i, newName, newDescription)
          n = i.updated(newName, newDescription)
        } yield n).onComplete {
          case Failure(_) => replyTo ! ItemCategoryResponse(Left[ValidationError, ItemCategory](RequestProcessingFailed))
          case Success(value) => replyTo ! ItemCategoryResponse(value)
        }(ctx.executionContext)
        Behaviors.same[ItemCategoryServerCommand]
      case RemoveItemCategory(id, replyTo) =>
        Future(for {
          i <- itemCategoryRepository.findById(id)
          _ <- itemCategoryRepository.remove(i)
        } yield ()).onComplete {
          case Failure(_) => replyTo ! EmptyResponse(Left[ValidationError, Unit](RequestProcessingFailed))
          case Success(value) => replyTo ! EmptyResponse(value)
        }(ctx.executionContext)
        Behaviors.same[ItemCategoryServerCommand]
    }
  }
}
