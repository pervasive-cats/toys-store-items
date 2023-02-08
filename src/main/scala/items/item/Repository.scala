/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item

import AnyOps.*
import items.catalogitem.Repository as CatalogItemRepository
import items.catalogitem.Repository.CatalogItemNotFound
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.catalogitem.valueobjects.Currency.findValues
import items.item.entities.{InCartItem, InPlaceItem, Item, ReturnedItem}
import items.item.valueobjects.{Customer, ItemId}
import items.itemcategory.valueobjects.ItemCategoryId
import items.{Validated, ValidationError}

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import enumeratum.*
import eu.timepit.refined.auto.autoUnwrap
import io.getquill.*

import scala.util.Try

trait Repository {

  def findById(itemId: ItemId, catalogItemId: CatalogItemId, store: Store)(using CatalogItemRepository): Validated[Item]

  def findAllReturned()(using CatalogItemRepository): Validated[Set[Validated[ReturnedItem]]]

  def add(inPlaceItem: InPlaceItem): Validated[Unit]

  def update(item: Item): Validated[Unit]

  def remove(item: Item): Validated[Unit]
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

    override def findById(itemId: ItemId, catalogItemId: CatalogItemId, store: Store)(using CatalogItemRepository): Validated[Item] =
      protectFromException(
        ctx.transaction(
          summon[CatalogItemRepository].findById(catalogItemId, store)
            .flatMap(catalogItem =>
              ctx
                .run(queryByKeys(itemId, catalogItemId, store))
                .map(i => i.isReturned match {
                  case "in_place" => Right[ValidationError, Item](InPlaceItem(itemId, catalogItem))
                  case "returned" => Right[ValidationError, Item](ReturnedItem(itemId, catalogItem))
                  case "in_cart" => Customer(i.customer).map(InCartItem(itemId, catalogItem, _))
                })
                .headOption
                .getOrElse(Left[ValidationError, Item](ItemNotFound))
            )
        )
      )

    override def add(inPlaceItem: InPlaceItem): Validated[Unit] =
      protectFromException(
        ctx.transaction {
          if(ctx.run(queryByKeys(inPlaceItem.id, inPlaceItem.kind.id, inPlaceItem.kind.store).nonEmpty))
            Left[ValidationError, Unit](ItemAlreadyPresent)
          else if (
            ctx.run(
              query[Items]
                .insert(
                  _.id -> lift[Long](inPlaceItem.id.value),
                  _.store -> lift[Long](inPlaceItem.kind.store.id.value),
                  _.catalogItemId -> lift[Long](inPlaceItem.kind.id.value),
                  _.isReturned -> sql"${lift[String]("in_place")}::item_status".as[String]
                )
            )
              !==
              1L
          )
            Left[ValidationError, Unit](OperationFailed)
          else
            Right[ValidationError, Unit](())
        }
      )

    override def remove(item: Item): Validated[Unit] =
      protectFromException(
        if (ctx.run(queryByKeys(item.id, item.kind.id, item.kind.store).delete) !== 1L)
          Left[ValidationError, Unit](OperationFailed)
        else
          Right[ValidationError, Unit](())
      )

    private def queryUpdate(item: Item, itemStatus: String): Boolean =
      ctx.run(
        queryByKeys(item.id, item.kind.id, item.kind.store)
          .update(
            _.isReturned -> sql"${lift[String](itemStatus)}::item_status".as[String]
          )
      )
      !==
      1L

    override def update(item: Item): Validated[Unit] =
      val ret: Boolean = item match {
        case inCartItem: InCartItem => ctx
          .run(
            queryByKeys(inCartItem.id, inCartItem.kind.id, inCartItem.kind.store)
              .update(
                _.customer -> lift[String](inCartItem.customer.email),
                _.isReturned -> sql"${lift[String]("in_cart")}::item_status".as[String]
              )
          )
          !==
          1L
        case _: InPlaceItem => queryUpdate(item, "in_place")
        case _: ReturnedItem => queryUpdate(item, "returned")
      }
      if (ret)
        Left[ValidationError, Unit](OperationFailed)
      else
        Right[ValidationError, Unit](())

    override def findAllReturned()(using CatalogItemRepository): Validated[Set[Validated[ReturnedItem]]] =
      protectFromException(
        Try(
          ctx
            .run(
              query[Items]
                .filter(_.isReturned === sql"${lift[String]("returned")}::item_status".as[String])
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
