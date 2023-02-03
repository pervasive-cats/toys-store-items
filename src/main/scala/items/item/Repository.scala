/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item

import items.{Validated, ValidationError}
import items.catalogitem.valueobjects.{CatalogItemId, Store}
import items.item.entities.{Item, ReturnedItem}
import items.item.valueobjects.ItemId

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.getquill.{PostgresJdbcContext, SnakeCase}

trait Repository {

  //def findById(itemId: ItemId, catalogItemId: CatalogItemId, store: Store): Validated[Item]

  //def findAllReturned(): Validated[Set[ReturnedItem]]

  //def add(item: Item): Validated[Unit]

  //def update(item: Item): Validated[Unit]

  //def remove(item: Item): Validated[Unit]
}

object Repository {

  case object ItemAlreadyPresent extends ValidationError {

    override val message: String = "The item was already registered"
  }

  case object ItemNotFound extends ValidationError {

    override val message: String = "No item found for the username that was provided"
  }

  case object OperationFailed extends ValidationError {

    override val message: String = "The operation on the given item has failed"
  }

  private class PostgresRepository(ctx: PostgresJdbcContext[SnakeCase]) extends Repository {

    import ctx.*
  }

  def apply: Repository = PostgresRepository(PostgresJdbcContext[SnakeCase](SnakeCase, "ctx"))

  def withPort(port: Int): Repository =
    PostgresRepository(
      PostgresJdbcContext[SnakeCase](
        SnakeCase,
        ConfigFactory.load().getConfig("ctx").withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(port))
      )
    )
}
