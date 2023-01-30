/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem

import io.github.pervasivecats.items.itemcategory.Repository
import io.github.pervasivecats.items.itemcategory.Repository.OperationFailed
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import eu.timepit.refined.auto.autoUnwrap
import io.getquill.*
import io.getquill.autoQuote
import items.{Validated, ValidationError}
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.{Amount, CatalogItemId, Currency, Price, Store}
import AnyOps.*

import scala.language.postfixOps

trait Repository {

  def findById(catalogItemId: CatalogItemId, store: Store): Validated[CatalogItem]

  //def findAllLifted(): List[Either[ValidationError, LiftedCatalogItem]]

  def add(catalogItem: InPlaceCatalogItem): Validated[Unit]

  def update(catalogItem: CatalogItem, price: Price): Validated[Unit]

  def remove(catalogItem: CatalogItem): Validated[Unit]
}

object Repository {

  case object CatalogItemNotFound extends ValidationError {

    override val message: String = "The queried customer was not found"
  }

  case object OperationFailed extends ValidationError {

    override val message: String = "The operation on the given catalog item was not correctly performed"
  }

  private class PostgresRepository(ctx: PostgresJdbcContext[SnakeCase]) extends Repository {

    import ctx.*

    private case class CatalogItems(id: Long, category: Long, store: Long, amount: Double, currency: String, is_lifted: Boolean)

    private case class CatalogItemsWithoutKeys(category: Long, amount: Double, currency: String)

    override def findById(catalogItemId: CatalogItemId, store: Store): Validated[CatalogItem] =
      ctx
        .run(
          quote {
            query[CatalogItems]
              .filter(_.id === lift[Long](catalogItemId.value))
              .filter(_.store === lift[Long](store.id))
          }
        )
        .map(c =>
          for {
            category <- ItemCategoryId(c.category)
            amount <- Amount(c.amount)
          } yield
            if (c.is_lifted)
              LiftedCatalogItem(catalogItemId, category, store, Price(amount, Currency.withName(String.valueOf(c.currency))))
            else
              InPlaceCatalogItem(catalogItemId, category, store, Price(amount, Currency.withName(String.valueOf(c.currency))))
        )
        .headOption
        .getOrElse(Left[ValidationError, CatalogItem](CatalogItemNotFound))

    /*override def findAllLifted(): List[Either[ValidationError, LiftedCatalogItem]] =
      ctx
        .run(
          query[CatalogItems]
            .filter(_.is_lifted === true)CatalogItem
        )
        .map(c =>
          for {
            id <- CatalogItemId(c.id)
            category <- ItemCategoryId(c.category)
            store <- Store(c.store)
            price <- for {
              amount <- Amount(c.amount)
            } yield Price(amount, Currency.withName(c.currency))
          } yield LiftedCatalogItem(id, category, store, price)
        )*/

    override def add(catalogItem: InPlaceCatalogItem): Validated[Unit] =
      if (
        ctx
          .run(
            querySchema[CatalogItemsWithoutKeys](entity = "catalog_items")
              .insertValue(
                lift(
                  CatalogItemsWithoutKeys(
                    catalogItem.category.value,
                    catalogItem.price.amount.value,
                    String.valueOf(catalogItem.price.currency)
                  )
                )
              )
          )
        !==
        1L
      ) Left[ValidationError, Unit](OperationFailed)
      else
        Right[ValidationError, Unit](())

    override def update(catalogItem: CatalogItem, price: Price): Validated[Unit] =
      val isLifted: Boolean = catalogItem match {
        case _: InPlaceCatalogItem => false
        case _: LiftedCatalogItem => true
      }
      if (
        ctx
          .run(
            query[CatalogItems]
              .filter(_.id === lift[Long](catalogItem.id.value))
              .filter(_.store === lift[Long](catalogItem.store.id))
              .updateValue(
                lift[CatalogItems](
                  CatalogItems(
                    catalogItem.id.value,
                    catalogItem.category.value,
                    catalogItem.store.id,
                    price.amount.value,
                    String.valueOf(price.currency),
                    isLifted
                  )
                )
              )
          )
        !==
        1L
      ) Left[ValidationError, Unit](OperationFailed)
      else
        Right[ValidationError, Unit](())

    override def remove(catalogItem: CatalogItem): Validated[Unit] =
      if (
        ctx.run(
          query[CatalogItems]
            .filter(_.id === lift[Long](catalogItem.id.value))
            .delete
        )
        !==
        1L
      ) Left[ValidationError, Unit](OperationFailed)
      else
        Right[ValidationError, Unit](())
  }

  def apply(config: Config): Repository = PostgresRepository(PostgresJdbcContext[SnakeCase](SnakeCase, config))
}
