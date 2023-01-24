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
import io.github.pervasivecats.AnyOps.===
import eu.timepit.refined.auto.given

trait Repository {

  def findById(id: ItemCategoryId): Validated[ItemCategory]

  //def add(name: Name, description: Description): Validated[Unit]

  //def update(itemCategory: ItemCategory, name: Name, description: Description): Validated[Unit]

  //def remove(itemCategory: ItemCategory): Validated[Unit]
}

object Repository {

  case object ItemCategoryNotFound extends ValidationError {

    override val message: String = "No item category found for the id that was provided"
  }

  private class PostgresRepository(ctx: PostgresJdbcContext[SnakeCase]) extends Repository {

    import ctx.*

    private case class ItemCategories(id: Long, name: String, description: String)

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