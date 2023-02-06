/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item

import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import eu.timepit.refined.auto.autoUnwrap
import io.getquill.*

import AnyOps.*
import items.catalogitem.Repository as CatalogItemRepository
import items.catalogitem.Repository.CatalogItemNotFound
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.item.entities.{InCartItem, InPlaceItem, Item, ReturnedItem}
import items.item.valueobjects.{Customer, ItemId}
import items.itemcategory.valueobjects.ItemCategoryId
import items.{Validated, ValidationError}

trait Repository {

  def findById(itemId: ItemId, catalogItemId: CatalogItemId, store: Store)(using CatalogItemRepository): Validated[Item]

  def findAllReturned()(using CatalogItemRepository): Validated[Set[Validated[ReturnedItem]]]

  def add(catalogItemId: CatalogItemId, customer: Customer, store: Store)(using CatalogItemRepository): Validated[InPlaceItem]

  def update(item: Item, catalogItemId: CatalogItemId, store: Store): Validated[Unit]

  def remove(itemId: ItemId, catalogItemId: CatalogItemId, store: Store): Validated[Unit]
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

    private case class Items(id: Long, catalogItemId: Long, customer: String, store: Long, isReturned: String)

    private def protectFromException[A](f: => Validated[A]): Validated[A] =
      Try(f).getOrElse(Left[ValidationError, A](OperationFailed))

    private def queryByKeys(itemId: ItemId, catalogItemId: CatalogItemId, store: Store) = quote {
      query[Items]
        .filter(_.id === lift[Long](itemId.value))
        .filter(_.catalogItemId === lift[Long](catalogItemId.value))
        .filter(_.store === lift[Long](store.id))
    }

    override def findById(
      itemId: ItemId,
      catalogItemId: CatalogItemId,
      store: Store
    )(
      using
      CatalogItemRepository
    ): Validated[Item] =
      protectFromException(
        ctx
          .run(queryByKeys(itemId, catalogItemId, store))
          .map(i =>
            for {
              customer <- Customer(i.customer)
              catalogItem <- summon[CatalogItemRepository].findById(catalogItemId, store)
            } yield i.isReturned match
              case "in_place" => InPlaceItem(itemId, catalogItem)
              case "in_cart" => InCartItem(itemId, catalogItem, customer)
              case "returned" => ReturnedItem(itemId, catalogItem)
          )
          .headOption
          .getOrElse(Left[ValidationError, Item](ItemNotFound))
      )

    override def add(
      catalogItemId: CatalogItemId,
      customer: Customer,
      store: Store
    )(
      using
      CatalogItemRepository
    ): Validated[InPlaceItem] =
      protectFromException(
        ctx.transaction {
          val nextId: Long =
            ctx
              .run(
                query[Items]
                  .filter(_.catalogItemId === lift[Long](catalogItemId.value))
                  .filter(_.store === lift[Long](store.id.value))
                  .map(_.id)
                  .max
              )
              .fold(0L)(_ + 1)
          if (
            ctx.run(
              query[Items]
                .insert(
                  _.id -> lift[Long](nextId),
                  _.store -> lift[Long](store.id),
                  _.catalogItemId -> lift[Long](catalogItemId.value),
                  _.customer -> lift[String](customer.email),
                  _.isReturned -> lift[String]("in_place")
                )
            )
            !==
            1L
          )
            Left[ValidationError, InPlaceItem](OperationFailed)
          else
            for {
              itemId <- ItemId(nextId)
              kind <- summon[CatalogItemRepository].findById(catalogItemId, store)
            } yield InPlaceItem(itemId, kind)
        }
      )

    override def remove(itemId: ItemId, catalogItemId: CatalogItemId, store: Store): Validated[Unit] =
      protectFromException(
        if (ctx.run(queryByKeys(itemId, catalogItemId, store).delete) !== 1L)
          Left[ValidationError, Unit](OperationFailed)
        else
          Right[ValidationError, Unit](())
      )

    override def update(item: Item, catalogItemId: CatalogItemId, store: Store): Validated[Unit] =
      val itemStatus: String = item match
        case _: InCartItem => "in_cart"
        case _: InPlaceItem => "in_place"
        case _: ReturnedItem => "returned"
      if (
        ctx.run(
          queryByKeys(item.id, catalogItemId, store)
            .update(
              _.isReturned -> lift[String](itemStatus)
            )
        )
        !==
        1L
      )
        Left[ValidationError, Unit](OperationFailed)
      else
        Right[ValidationError, Unit](())

    override def findAllReturned()(using CatalogItemRepository): Validated[Set[Validated[ReturnedItem]]] =
      protectFromException(
        Try(
          ctx
            .run(
              query[Items]
                .filter(_.isReturned === lift[String]("returned"))
            )
            .map(r =>
              for {
                validatedKind <- for {
                  catalogItemId <- CatalogItemId(r.catalogItemId)
                  store <- Store(r.store)
                } yield summon[CatalogItemRepository].findById(catalogItemId, store)
                id <- ItemId(r.id)
                kind <- validatedKind
              } yield ReturnedItem(id, kind)
            )
            .toSet
        )
          .toEither
          .map(Right[ValidationError, Set[Validated[ReturnedItem]]])
          .getOrElse(Left[ValidationError, Set[Validated[ReturnedItem]]](OperationFailed))
      )
  }

  def apply(config: Config): Repository = PostgresRepository(PostgresJdbcContext[SnakeCase](SnakeCase, config))
}
