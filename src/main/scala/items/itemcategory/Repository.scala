/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.itemcategory

import items.{Validated, ValidationError}
import items.itemcategory.entities.ItemCategory
import items.itemcategory.valueobjects.*

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.getquill.*
import AnyOps.{!==, ===}
import io.getquill.autoQuote
import eu.timepit.refined.auto.given

trait Repository {

  def findById(id: ItemCategoryId): Validated[ItemCategory]

  def add(name: Name, description: Description): Validated[Unit]

  def update(itemCategory: ItemCategory, name: Name, description: Description): Validated[Unit]

  // def remove(itemCategory: ItemCategory): Validated[Unit]

  def selectAll(): List[Long]
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

    private def queryById(id: ItemCategoryId) = quote {
      query[ItemCategories].filter(_.id === lift[Long](id.value))
    }
    override def findById(id: ItemCategoryId): Validated[ItemCategory] = {
      ctx
        .run(queryById(id))
        .map(item =>
          for {
            id <- ItemCategoryId(item.id)
            name <- Name(item.name)
            description <- Description(item.description)
          } yield ItemCategory(id, name, description))
        .headOption
        .getOrElse(Left[ValidationError, ItemCategory](ItemCategoryNotFound))
    }

    override def add(name: Name, description: Description): Validated[Unit] = {
      if (
        ctx
          .run(querySchema[ItemCategoriesWithoutId](entity = "item_categories")
            .insertValue(
              lift(
                ItemCategoriesWithoutId(
                  name.name,
                  description.description
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
      if(
        ctx
          .run(
            query[ItemCategories]
              .filter(_.id === lift[Long](itemCategory.id.value))
              .updateValue(lift(ItemCategories(9002, name.name, description.description)))
          ) !== 1L
      ) Left[ValidationError, Unit](OperationFailed)
      else
        Right[ValidationError, Unit](())
    }



    override def selectAll(): List[Long] = {
      ctx
        .run(query[ItemCategories])
        .map(elem => elem.id)
    }

  }



  def apply: Repository = PostgresRepository(PostgresJdbcContext[SnakeCase](SnakeCase, "ctx"))

  def withPort(port: Int): Repository =
    PostgresRepository(
      PostgresJdbcContext[SnakeCase](
        SnakeCase,
        ConfigFactory
          .load()
          .getConfig("ctx")
          .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(port))
      )
    )
}