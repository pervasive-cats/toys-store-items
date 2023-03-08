/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory

import javax.sql.DataSource

import scala.util.Try

import io.github.pervasivecats.Validated
import io.github.pervasivecats.ValidationError

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import eu.timepit.refined.auto.given
import io.getquill.*
import io.getquill.autoQuote

import items.itemcategory.entities.ItemCategory
import items.itemcategory.valueobjects.*
import AnyOps.{!==, ===}
import items.RepositoryOperationFailed

trait Repository {

  def findById(id: ItemCategoryId): Validated[ItemCategory]

  def add(name: Name, description: Description): Validated[ItemCategory]

  def update(itemCategory: ItemCategory, name: Name, description: Description): Validated[Unit]

  def remove(itemCategory: ItemCategory): Validated[Unit]
}

object Repository {

  case object ItemCategoryNotFound extends ValidationError {

    override val message: String = "No item category found for the id that was provided"
  }

  private class PostgresRepository(ctx: PostgresJdbcContext[SnakeCase]) extends Repository {

    import ctx.*

    private case class ItemCategories(id: Long, name: String, description: String)

    private def protectFromException[A](f: => Validated[A]): Validated[A] =
      Try(f).getOrElse(Left[ValidationError, A](RepositoryOperationFailed))

    override def findById(id: ItemCategoryId): Validated[ItemCategory] = protectFromException {
      ctx
        .run(query[ItemCategories].filter(_.id === lift[Long](id.value)))
        .map(item =>
          for {
            id <- ItemCategoryId(item.id)
            name <- Name(item.name)
            description <- Description(item.description)
          } yield ItemCategory(id, name, description)
        )
        .headOption
        .getOrElse(Left[ValidationError, ItemCategory](ItemCategoryNotFound))
    }

    override def add(name: Name, description: Description): Validated[ItemCategory] = protectFromException {
      ItemCategoryId(
        ctx
          .run(
            query[ItemCategories]
              .insert(
                _.name -> lift[String](name.value),
                _.description -> lift[String](description.value)
              )
              .returningGenerated(_.id)
          )
      ).map(ItemCategory(_, name, description))
    }

    override def update(itemCategory: ItemCategory, name: Name, description: Description): Validated[Unit] =
      protectFromException {
        if (
          ctx
            .run(
              query[ItemCategories]
                .filter(_.id === lift[Long](itemCategory.id.value))
                .updateValue(
                  lift(
                    ItemCategories(
                      itemCategory.id.value,
                      name.value,
                      description.value
                    )
                  )
                )
            )
          !==
          1L
        ) Left[ValidationError, Unit](RepositoryOperationFailed)
        else
          Right[ValidationError, Unit](())
      }

    override def remove(itemCategory: ItemCategory): Validated[Unit] = protectFromException {
      if (
        ctx
          .run(
            query[ItemCategories]
              .filter(_.id === lift[Long](itemCategory.id.value))
              .delete
          )
        !==
        1L
      ) Left[ValidationError, Unit](RepositoryOperationFailed)
      else
        Right[ValidationError, Unit](())
    }
  }

  def apply(dataSource: DataSource): Repository = PostgresRepository(PostgresJdbcContext[SnakeCase](SnakeCase, dataSource))
}
