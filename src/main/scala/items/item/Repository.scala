/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item

import items.{Validated, ValidationError}
import items.catalogitem.valueobjects.{Amount, CatalogItemId, Currency, Price, Store}
import items.item.entities.{InCartItem, InPlaceItem, Item, ReturnedItem}
import items.item.valueobjects.{Customer, ItemId}
import io.getquill.*
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.getquill.{PostgresJdbcContext, SnakeCase, query, querySchema, quote}
import io.github.pervasivecats.AnyOps.*
import eu.timepit.refined.auto.autoUnwrap
import io.github.pervasivecats.items.catalogitem.Repository.CatalogItemNotFound
import io.github.pervasivecats.items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId
import items.catalogitem.Repository as CatalogItemRepository

import scala.util.Try

trait Repository {

  def findById(itemId: ItemId, catalogItemId: CatalogItemId, store: Store): Validated[Item]

  //def findAllReturned(): Validated[Set[ReturnedItem]]

  def add(catalogItemId: CatalogItemId, customer: Customer, store: Store): Validated[Item]

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

    private case class Items(id: Long, catalogItemId: Long, customer: String, store: Long, isReturned: String)

    private case class CatalogItems(id: Long, category: Long, store: Long, amount: Double, currency: String, isLifted: Boolean)

    private def protectFromException[A](f: => Validated[A]): Validated[A] =
      Try(f).getOrElse(Left[ValidationError, A](OperationFailed))

    private def findCatalogItemById(catalogItemId: CatalogItemId, store: Store): Validated[CatalogItem] =
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

    override def findById(itemId: ItemId, catalogItemId: CatalogItemId, store: Store): Validated[Item] =
      ctx
        .run(
          quote {
            query[Items]
              .filter(_.id === lift[Long](itemId.value))
              .filter(_.catalogItemId === lift[Long](catalogItemId.value))
              .filter(_.store === lift[Long](store.id))
          }
        ).map(i =>
        for {
          customer <- Customer(i.customer)
          catalogItem <- findCatalogItemById(catalogItemId, store)
        } yield
        i.isReturned match
          case "in_place" => InPlaceItem(itemId, catalogItem)
          case "in_cart" => InCartItem(itemId, catalogItem, customer)
          case "returned" => ReturnedItem(itemId, catalogItem)
        )
        .headOption
        .getOrElse(Left[ValidationError, Item](ItemNotFound))

    override def add(catalogItemId: CatalogItemId, customer: Customer, store: Store): Validated[Item] =
      ctx.transaction{
        val nextId: Long =
          ctx.run(
            query[Items]
              .filter(_.id === lift[Long](store.id.value))
              .filter(_.catalogItemId === lift[Long](catalogItemId.value))
              .map(_.id)
              .max
          )
            .fold(0L)(_ + 1)
        val s = 3
        if (
          ctx.run(
            query[Items]
              .insert(
                _.id -> lift[Long](nextId),
                _.store -> lift[Long](store.id),
                _.catalogItemId -> lift[Long](catalogItemId.value),
                _.customer -> lift[String](customer.email),
                _.isReturned -> lift("in_place")
              )
          )
            !==
            1L
        )
          Left[ValidationError, Item](OperationFailed)
        else
          for {
            itemId <- ItemId(nextId)
            kind <- findCatalogItemById(catalogItemId, store)
          } yield InPlaceItem(itemId, kind)
      }

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
