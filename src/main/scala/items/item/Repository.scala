/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item

import javax.sql.DataSource

import scala.util.Try

import io.github.pervasivecats.Validated

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import enumeratum.*
import eu.timepit.refined.auto.autoUnwrap
import io.getquill.*

import AnyOps.*
import items.catalogitem.Repository as CatalogItemRepository
import items.catalogitem.Repository.CatalogItemNotFound
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.*
import items.catalogitem.valueobjects.Currency.findValues
import items.item.entities.{InCartItem, InPlaceItem, Item, ReturnedItem}
import items.item.valueobjects.{Customer, ItemId}
import items.itemcategory.valueobjects.ItemCategoryId
import items.RepositoryOperationFailed

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

  private class PostgresRepository(ctx: PostgresJdbcContext[SnakeCase]) extends Repository {

    import ctx.*

    private case class Items(id: Long, catalogItemId: Long, customer: String, store: Long, status: String)

    private enum ItemStatus(val status: String) {
      case InPlace extends ItemStatus("in_place")
      case InCart extends ItemStatus("in_cart")
      case Returned extends ItemStatus("returned")
    }

    private def protectFromException[A](f: => Validated[A]): Validated[A] =
      Try(f).getOrElse(Left[ValidationError, A](RepositoryOperationFailed))

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
        ctx.transaction(
          summon[CatalogItemRepository]
            .findById(catalogItemId, store)
            .flatMap(catalogItem =>
              ctx
                .run(queryByKeys(itemId, catalogItemId, store))
                .map(i =>
                  i.status match {
                    case ItemStatus.InPlace.status => Right[ValidationError, Item](InPlaceItem(itemId, catalogItem))
                    case ItemStatus.Returned.status => Right[ValidationError, Item](ReturnedItem(itemId, catalogItem))
                    case ItemStatus.InCart.status => Customer(i.customer).map(InCartItem(itemId, catalogItem, _))
                  }
                )
                .headOption
                .getOrElse(Left[ValidationError, Item](ItemNotFound))
            )
        )
      )

    override def add(inPlaceItem: InPlaceItem): Validated[Unit] =
      protectFromException(
        ctx.transaction {
          if (ctx.run(queryByKeys(inPlaceItem.id, inPlaceItem.kind.id, inPlaceItem.kind.store).nonEmpty))
            Left[ValidationError, Unit](ItemAlreadyPresent)
          else if (
            ctx.run(
              query[Items]
                .insert(
                  _.id -> lift[Long](inPlaceItem.id.value),
                  _.store -> lift[Long](inPlaceItem.kind.store.id.value),
                  _.catalogItemId -> lift[Long](inPlaceItem.kind.id.value),
                  _.status -> sql"${lift[String](ItemStatus.InPlace.status)}::item_status".as[String]
                )
            )
            !==
            1L
          )
            Left[ValidationError, Unit](RepositoryOperationFailed)
          else
            Right[ValidationError, Unit](())
        }
      )

    override def remove(item: Item): Validated[Unit] =
      protectFromException(
        if (ctx.run(queryByKeys(item.id, item.kind.id, item.kind.store).delete) !== 1L)
          Left[ValidationError, Unit](RepositoryOperationFailed)
        else
          Right[ValidationError, Unit](())
      )

    private def queryUpdate(item: Item, itemStatus: String): Boolean =
      ctx.run(
        queryByKeys(item.id, item.kind.id, item.kind.store)
          .update(
            _.status -> sql"${lift[String](itemStatus)}::item_status".as[String]
          )
      )
      !==
      1L

    override def update(item: Item): Validated[Unit] =
      protectFromException(
        if (
          item match {
            case inCartItem: InCartItem =>
              ctx
                .run(
                  queryByKeys(inCartItem.id, inCartItem.kind.id, inCartItem.kind.store)
                    .update(
                      _.customer -> lift[String](inCartItem.customer.email),
                      _.status -> sql"${lift[String](ItemStatus.InCart.status)}::item_status".as[String]
                    )
                )
              !==
              1L
            case _: InPlaceItem => queryUpdate(item, ItemStatus.InPlace.status)
            case _: ReturnedItem => queryUpdate(item, ItemStatus.Returned.status)
          }
        )
          Left[ValidationError, Unit](RepositoryOperationFailed)
        else
          Right[ValidationError, Unit](())
      )

    override def findAllReturned()(using CatalogItemRepository): Validated[Set[Validated[ReturnedItem]]] =
      protectFromException(
        Try(
          ctx
            .run(
              query[Items]
                .filter(_.status === sql"${lift[String](ItemStatus.Returned.status)}::item_status".as[String])
            )
            .map(r =>
              for {
                kind <- (for {
                  catalogItemId <- CatalogItemId(r.catalogItemId)
                  store <- Store(r.store)
                } yield summon[CatalogItemRepository].findById(catalogItemId, store)).flatten
                id <- ItemId(r.id)
              } yield ReturnedItem(id, kind)
            )
            .toSet
        )
          .toEither
          .map(Right[ValidationError, Set[Validated[ReturnedItem]]])
          .getOrElse(Left[ValidationError, Set[Validated[ReturnedItem]]](RepositoryOperationFailed))
      )
  }

  def apply(dataSource: DataSource): Repository = PostgresRepository(PostgresJdbcContext[SnakeCase](SnakeCase, dataSource))
}
