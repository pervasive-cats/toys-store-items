/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application

import scala.util.control.Exception.Described

import io.github.pervasivecats.Validated

import eu.timepit.refined.auto.given
import spray.json.DefaultJsonProtocol
import spray.json.JsBoolean
import spray.json.JsNumber
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.deserializationError
import spray.json.enrichAny

import items.itemcategory.valueobjects.{Description, ItemCategoryId, Name}
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.valueobjects.{Amount, CatalogItemId, Count, Currency, Price, Store}
import items.itemcategory.entities.ItemCategory

object Serializers extends DefaultJsonProtocol {

  private def stringSerializer[A](extractor: A => String, builder: String => Validated[A]): JsonFormat[A] = new JsonFormat[A] {

    override def read(json: JsValue): A = json match {
      case JsString(value) => builder(value).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(obj: A): JsValue = extractor(obj).toJson
  }

  given JsonFormat[Name] = stringSerializer(_.value, Name.apply)

  given JsonFormat[Description] = stringSerializer(_.value, Description.apply)

  private def longSerializer[A](extractor: A => Long, builder: Long => Validated[A]): JsonFormat[A] = new JsonFormat[A] {

    override def read(json: JsValue): A = json match {
      case JsNumber(value) if value.isValidLong =>
        builder(value.longValue).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(obj: A): JsValue = extractor(obj).toJson
  }

  given JsonFormat[ItemCategoryId] = longSerializer(_.value, ItemCategoryId.apply)

  given JsonFormat[Amount] with {

    override def read(json: JsValue): Amount = json match {
      case JsNumber(value) if value.isExactDouble =>
        Amount(value.doubleValue).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(amount: Amount): JsValue = (amount.value: Double).toJson
  }

  given JsonFormat[CatalogItemId] = longSerializer(_.value, CatalogItemId.apply)

  given JsonFormat[Currency] with {

    override def read(json: JsValue): Currency = json match {
      case JsString(value) => Currency.withNameEither(value).fold(e => deserializationError(e.getMessage), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(currency: Currency): JsValue = JsString(currency.entryName)
  }

  given JsonFormat[Price] with {

    override def read(json: JsValue): Price = json.asJsObject.getFields("amount", "currency") match {
      case Seq(JsNumber(amount), JsString(currency)) if amount.isDecimalDouble && Currency.withNameOption(currency).isDefined =>
        (for {
          a <- Amount(amount.doubleValue)
          c = Currency.withName(currency)
        } yield Price(a, c)).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(price: Price): JsValue = JsObject(
      "amount" -> price.amount.toJson,
      "currency" -> price.currency.toJson
    )
  }

  given JsonFormat[Store] = longSerializer(_.id, Store.apply)

  given JsonFormat[Count] = longSerializer(_.value, Count.apply)

  given JsonFormat[ItemCategory] with {

    override def read(json: JsValue): ItemCategory = json.asJsObject.getFields("id", "name", "description") match {
      case Seq(JsNumber(id), JsString(name), JsString(description)) if id.isValidLong =>
        (for {
          i <- ItemCategoryId(id.longValue)
          n <- Name(name)
          d <- Description(description)
        } yield ItemCategory(i, n, d)).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(itemCategory: ItemCategory): JsValue = JsObject(
      "id" -> itemCategory.id.toJson,
      "name" -> itemCategory.name.toJson,
      "description" -> itemCategory.description.toJson
    )
  }

  given JsonFormat[CatalogItem] with {

    override def read(json: JsValue): CatalogItem =
      json.asJsObject.getFields("id", "category", "store", "price", "count") match {
        case Seq(JsNumber(id), JsNumber(category), JsNumber(store), price, JsNumber(count))
             if id.isValidLong && category.isValidLong && store.isValidLong && count.isValidLong =>
          (for {
            i <- CatalogItemId(id.longValue)
            ci <- ItemCategoryId(category.longValue)
            s <- Store(store.longValue)
            p = price.convertTo[Price]
            c <- if (count.longValue > 0) Count(count.longValue).map(Some(_)) else Right[ValidationError, Option[Count]](None)
          } yield c.fold(InPlaceCatalogItem(i, ci, s, p))(LiftedCatalogItem(i, ci, s, p, _)))
            .fold(e => deserializationError(e.message), identity)
        case _ => deserializationError(msg = "Json format is not valid")
      }

    override def write(catalogItem: CatalogItem): JsValue = JsObject(
      "id" -> catalogItem.id.toJson,
      "category" -> catalogItem.category.toJson,
      "store" -> catalogItem.store.toJson,
      "price" -> catalogItem.price.toJson,
      "count" -> (catalogItem match {
        case item: InPlaceCatalogItem => JsNumber(0L)
        case item: LiftedCatalogItem => item.count.toJson
      })
    )
  }
}
