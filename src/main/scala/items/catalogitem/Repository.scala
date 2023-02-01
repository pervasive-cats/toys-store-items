/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem

import scala.language.postfixOps
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import eu.timepit.refined.auto.autoUnwrap
import io.getquill.*

import AnyOps.*
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.itemcategory.Repository
import items.itemcategory.Repository.OperationFailed
import items.itemcategory.valueobjects.ItemCategoryId
import items.{Validated, ValidationError}

trait Repository {

  def findById(catalogItemId: CatalogItemId, store: Store): Validated[CatalogItem]

  def findAllLifted(): Validated[Set[Validated[LiftedCatalogItem]]]

  def add(category: ItemCategoryId, store: Store, price: Price): Validated[InPlaceCatalogItem]

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

    private case class CatalogItems(id: Long, category: Long, store: Long, amount: Double, currency: String, isLifted: Boolean)

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
            if (c.isLifted)
              LiftedCatalogItem(catalogItemId, category, store, Price(amount, Currency.withName(c.currency)))
            else
              InPlaceCatalogItem(catalogItemId, category, store, Price(amount, Currency.withName(c.currency)))
        )
        .headOption
        .getOrElse(Left[ValidationError, CatalogItem](CatalogItemNotFound))

    override def findAllLifted(): Validated[Set[Validated[LiftedCatalogItem]]] =
      Try(
        ctx
          .run(
            query[CatalogItems]
              .filter(_.isLifted)
          )
          .map(c =>
            for {
              id <- CatalogItemId(c.id)
              category <- ItemCategoryId(c.category)
              store <- Store(c.store)
              price <- Amount(c.amount).map(Price(_, Currency.withName(c.currency)))
            } yield LiftedCatalogItem(id, category, store, price)
          )
          .toSet
      )
        .toEither
        .map(Right[ValidationError, Set[Validated[LiftedCatalogItem]]])
        .getOrElse(Left[ValidationError, Set[Validated[LiftedCatalogItem]]](OperationFailed))

    override def add(category: ItemCategoryId, store: Store, price: Price): Validated[InPlaceCatalogItem] =
      ctx
        .transaction {
          val nextId: Long =
            ctx
              .run(
                query[CatalogItems]
                  .filter(_.store === lift[Long](store.id))
                  .map(_.id)
                  .max
              )
              .fold(0L)(_ + 1)
          if (
            ctx
              .run(
                query[CatalogItems]
                  .insert(
                    _.id -> lift[Long](nextId),
                    _.category -> lift[Long](category.value),
                    _.store -> lift[Long](store.id.value),
                    _.amount -> lift[Double](price.amount.value),
                    _.currency -> lift[String](String.valueOf(price.currency))
                  )
              )
              !==
              1L
          )
            Left[ValidationError, InPlaceCatalogItem](OperationFailed)
          else
            CatalogItemId(nextId).map(InPlaceCatalogItem(_, category, store, price))
        }

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
              .update(
                _.amount -> lift[Double](price.amount.value),
                _.currency -> lift[String](String.valueOf(price.currency)),
                _.isLifted -> lift[Boolean](isLifted)
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
            .filter(_.store === lift[Long](catalogItem.store.id))
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
