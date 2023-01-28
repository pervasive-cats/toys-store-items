/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.getquill.PostgresJdbcContext
import io.getquill.SnakeCase

import items.Validated
import items.catalogitem.entities.{CatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.{CatalogItemId, Price, Store}

trait Repository {

  // def findById(catalogItemId: CatalogItemId, store: Store): Validated[CatalogItem]

  // def findAllLifted(): Validated[Set[LiftedCatalogItem]]

  // def add(catalogItem: CatalogItem): Validated[Unit]

  // def update(catalogItem: CatalogItem, price: Price): Validated[Unit]

  // def remove(catalogItem: CatalogItem): Validated[Unit]
}

object Repository {

  private class PostgresRepository(ctx: PostgresJdbcContext[SnakeCase]) extends Repository {

    import ctx.*

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
