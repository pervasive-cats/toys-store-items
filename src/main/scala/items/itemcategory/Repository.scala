/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import eu.timepit.refined.auto.given
import io.getquill.*
import io.getquill.autoQuote

import items.{Validated, ValidationError}
import items.itemcategory.entities.ItemCategory
import items.itemcategory.valueobjects.*
import AnyOps.{!==, ===}

trait Repository {

  def findById(id: ItemCategoryId): Validated[ItemCategory]

  def add(name: Name, description: Description): Validated[Unit]

  def update(itemCategory: ItemCategory, name: Name, description: Description): Validated[Unit]

  def remove(itemCategory: ItemCategory): Validated[Unit]
}

object Repository {

  case object ItemCategoryNotFound extends ValidationError {

    override val message: String = "No item category found for the id that was provided"
  }

  case object OperationFailed extends ValidationError {

    override val message: String = "The operation on the given item category was not correctly performed"
  }

  private class PostgresRepository(ctx: PostgresJdbcContext[SnakeCase]) extends Repository {

    import ctx.*

    private case class ItemCategories(id: Long, name: String, description: String)

    private case class ItemCategoriesWithoutId(name: String, description: String)

    override def findById(id: ItemCategoryId): Validated[ItemCategory] = {
      ctx
        .run(quote {
          query[ItemCategories].filter(_.id === lift[Long](id.value))
        })
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

    override def add(name: Name, description: Description): Validated[Unit] = {
      if (
        ctx
          .run(
            querySchema[ItemCategoriesWithoutId](entity = "item_categories")
              .insertValue(
                lift(
                  ItemCategoriesWithoutId(
                    name.value,
                    description.value
                  )
                )
              )
          )
        !==
        1L
      ) Left[ValidationError, Unit](OperationFailed)
      else
        Right[ValidationError, Unit](())
    }

    override def update(itemCategory: ItemCategory, name: Name, description: Description): Validated[Unit] = {
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
          ) !== 1L
      ) Left[ValidationError, Unit](OperationFailed)
      else
        Right[ValidationError, Unit](())
    }

    override def remove(itemCategory: ItemCategory): Validated[Unit] = {
      if (
        ctx
          .run(
            query[ItemCategories]
              .filter(_.id === lift[Long](itemCategory.id.value))
              .delete
          ) !== 1L
      ) Left[ValidationError, Unit](OperationFailed)
      else
        Right[ValidationError, Unit](())
    }
  }

  def apply(config: Config): Repository = PostgresRepository(PostgresJdbcContext[SnakeCase](SnakeCase, config))
}
