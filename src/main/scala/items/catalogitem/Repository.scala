/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem

import javax.sql.DataSource

import scala.util.Try

import io.github.pervasivecats.Validated
import io.github.pervasivecats.ValidationError

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import eu.timepit.refined.auto.autoUnwrap
import io.getquill.*

import AnyOps.*
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.itemcategory.valueobjects.ItemCategoryId
import items.RepositoryOperationFailed

trait Repository {

  def findById(catalogItemId: CatalogItemId, store: Store): Validated[CatalogItem]

  def findAllLifted(): Validated[Set[Validated[LiftedCatalogItem]]]

  def add(category: ItemCategoryId, store: Store, price: Price): Validated[InPlaceCatalogItem]

  def update(catalogItem: CatalogItem, count: Option[Count], price: Price): Validated[Unit]

  def remove(catalogItem: CatalogItem): Validated[Unit]
}

object Repository {

  case object CatalogItemNotFound extends ValidationError {

    override val message: String = "The queried customer was not found"
  }

  private class PostgresRepository(ctx: PostgresJdbcContext[SnakeCase]) extends Repository {

    import ctx.*

    private case class CatalogItems(id: Long, category: Long, store: Long, amount: Double, currency: String, count: Long)

    private def protectFromException[A](f: => Validated[A]): Validated[A] =
      Try(f).getOrElse(Left[ValidationError, A](RepositoryOperationFailed))

    override def findById(catalogItemId: CatalogItemId, store: Store): Validated[CatalogItem] = protectFromException {
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
            count <- if (c.count > 0) Count(c.count).map(Some(_)) else Right[ValidationError, Option[Count]](None)
          } yield count.fold(
            InPlaceCatalogItem(catalogItemId, category, store, Price(amount, Currency.withName(c.currency)))
          )(
            LiftedCatalogItem(catalogItemId, category, store, Price(amount, Currency.withName(c.currency)), _)
          )
        )
        .headOption
        .getOrElse(Left[ValidationError, CatalogItem](CatalogItemNotFound))
    }

    override def findAllLifted(): Validated[Set[Validated[LiftedCatalogItem]]] =
      Try(
        ctx
          .run(query[CatalogItems].filter(_.count > 0))
          .map(c =>
            for {
              id <- CatalogItemId(c.id)
              category <- ItemCategoryId(c.category)
              store <- Store(c.store)
              price <- Amount(c.amount).map(Price(_, Currency.withName(c.currency)))
              count <- Count(c.count)
            } yield LiftedCatalogItem(id, category, store, price, count)
          )
          .toSet
      )
        .toEither
        .map(Right[ValidationError, Set[Validated[LiftedCatalogItem]]])
        .getOrElse(Left[ValidationError, Set[Validated[LiftedCatalogItem]]](RepositoryOperationFailed))

    override def add(category: ItemCategoryId, store: Store, price: Price): Validated[InPlaceCatalogItem] = protectFromException {
      ctx.transaction {
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
          ctx.run(
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
          Left[ValidationError, InPlaceCatalogItem](RepositoryOperationFailed)
        else
          CatalogItemId(nextId).map(InPlaceCatalogItem(_, category, store, price))
      }
    }

    override def update(catalogItem: CatalogItem, count: Option[Count], price: Price): Validated[Unit] =
      if (
        ctx.run(
          query[CatalogItems]
            .filter(_.id === lift[Long](catalogItem.id.value))
            .filter(_.store === lift[Long](catalogItem.store.id))
            .update(
              _.amount -> lift[Double](price.amount.value),
              _.currency -> lift[String](String.valueOf(price.currency)),
              _.count -> lift[Long](count.map(_.value: Long).getOrElse(0L))
            )
        )
        !==
        1L
      ) Left[ValidationError, Unit](RepositoryOperationFailed)
      else
        Right[ValidationError, Unit](())

    override def remove(catalogItem: CatalogItem): Validated[Unit] = protectFromException {
      if (
        ctx.run(
          query[CatalogItems]
            .filter(_.id === lift[Long](catalogItem.id.value))
            .filter(_.store === lift[Long](catalogItem.store.id))
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
