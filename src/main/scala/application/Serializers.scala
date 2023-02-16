/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application

import io.github.pervasivecats.Validated

import eu.timepit.refined.auto.given
import spray.json.DefaultJsonProtocol
import spray.json.JsBoolean
import spray.json.JsNull
import spray.json.JsNumber
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.deserializationError
import spray.json.enrichAny

import items.catalogitem.domainevents.{CatalogItemLifted, CatalogItemPutInPlace}
import items.catalogitem.entities.*
import items.catalogitem.valueobjects.*
import items.item.domainevents.*
import items.item.entities.*
import items.item.valueobjects.{Customer, ItemId}
import items.itemcategory.entities.ItemCategory
import items.itemcategory.valueobjects.*

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
      case JsNumber(value) if value.isDecimalDouble =>
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

  given JsonFormat[ItemId] = longSerializer(_.value, ItemId.apply)

  given JsonFormat[Customer] = stringSerializer(_.email, Customer.apply)

  given JsonFormat[Item] with {

    override def read(json: JsValue): Item = json.asJsObject.getFields("id", "kind", "customer", "state") match {
      case Seq(JsNumber(id), kind, JsString(customer), JsString(state)) if id.isValidLong =>
        (for {
          i <- ItemId(id.longValue)
          k = kind.convertTo[CatalogItem]
          c <- Customer(customer)
        } yield state match {
          case "InCartItem" => InCartItem(i, k, c)
          case _ => deserializationError(msg = "Json format is not valid")
        }).fold(e => deserializationError(e.message), identity)
      case Seq(JsNumber(id), kind, JsNull, JsString(state)) if id.isValidLong =>
        (for {
          i <- ItemId(id.longValue)
          k = kind.convertTo[CatalogItem]
        } yield state match {
          case "InPlaceItem" => InPlaceItem(i, k)
          case "ReturnedItem" => ReturnedItem(i, k)
          case _ => deserializationError(msg = "Json format is not valid")
        }).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(item: Item): JsValue = JsObject(
      "id" -> item.id.toJson,
      "kind" -> item.kind.toJson,
      "customer" -> (item match {
        case _: InPlaceItem => JsNull
        case _: ReturnedItem => JsNull
        case i: InCartItem => i.customer.toJson
      }),
      "state" -> (item match {
        case _: InPlaceItem => "InPlaceItem"
        case _: ReturnedItem => "ReturnedItem"
        case i: InCartItem => "InCartItem"
      }).toJson
    )
  }

  given JsonFormat[CatalogItemLifted] with {

    override def read(json: JsValue): CatalogItemLifted = json.asJsObject.getFields("type", "id", "store") match {
      case Seq(JsString("CatalogItemLifted"), JsNumber(id), JsNumber(store)) if id.isValidLong && store.isValidLong =>
        (for {
          i <- CatalogItemId(id.longValue)
          s <- Store(store.longValue)
        } yield CatalogItemLifted(i, s)).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(catalogItemLifted: CatalogItemLifted): JsValue = JsObject(
      "type" -> "CatalogItemLifted".toJson,
      "id" -> catalogItemLifted.catalogItemId.toJson,
      "store" -> catalogItemLifted.store.toJson
    )
  }

  given JsonFormat[CatalogItemPutInPlace] with {

    override def read(json: JsValue): CatalogItemPutInPlace = json.asJsObject.getFields("type", "id", "store") match {
      case Seq(JsString("CatalogItemPutInPlace"), JsNumber(id), JsNumber(store)) if id.isValidLong && store.isValidLong =>
        (for {
          i <- CatalogItemId(id.longValue)
          s <- Store(store.longValue)
        } yield CatalogItemPutInPlace(i, s)).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(catalogItemPutInPlace: CatalogItemPutInPlace): JsValue = JsObject(
      "type" -> "CatalogItemPutInPlace".toJson,
      "id" -> catalogItemPutInPlace.catalogItemId.toJson,
      "store" -> catalogItemPutInPlace.store.toJson
    )
  }

  given JsonFormat[ItemAddedToCart] with {

    override def read(json: JsValue): ItemAddedToCart =
      json.asJsObject.getFields("type", "id", "kind", "store", "customer") match {
        case Seq(JsString("ItemAddedToCart"), JsNumber(id), JsNumber(kind), JsNumber(store), JsString(customer))
             if id.isValidLong && kind.isValidLong && store.isValidLong =>
          (for {
            i <- ItemId(id.longValue)
            k <- CatalogItemId(kind.longValue)
            s <- Store(store.longValue)
            c <- Customer(customer)
          } yield ItemAddedToCart(k, s, i, c)).fold(e => deserializationError(e.message), identity)
        case _ => deserializationError(msg = "Json format is not valid")
      }

    override def write(itemAddedToCart: ItemAddedToCart): JsValue = JsObject(
      "type" -> "ItemAddedToCart".toJson,
      "id" -> itemAddedToCart.itemId.toJson,
      "kind" -> itemAddedToCart.catalogItemId.toJson,
      "store" -> itemAddedToCart.store.toJson,
      "customer" -> itemAddedToCart.customer.toJson
    )
  }

  given JsonFormat[ItemPutInPlace] with {

    override def read(json: JsValue): ItemPutInPlace = json.asJsObject.getFields("type", "id", "kind", "store") match {
      case Seq(JsString("ItemPutInPlace"), JsNumber(id), JsNumber(kind), JsNumber(store))
           if id.isValidLong && kind.isValidLong && store.isValidLong =>
        (for {
          i <- ItemId(id.longValue)
          k <- CatalogItemId(kind.longValue)
          s <- Store(store.longValue)
        } yield ItemPutInPlace(k, s, i)).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(itemPutInPlace: ItemPutInPlace): JsValue = JsObject(
      "type" -> "ItemPutInPlace".toJson,
      "id" -> itemPutInPlace.itemId.toJson,
      "kind" -> itemPutInPlace.catalogItemId.toJson,
      "store" -> itemPutInPlace.store.toJson
    )
  }

  given JsonFormat[ItemReturned] with {

    override def read(json: JsValue): ItemReturned = json.asJsObject.getFields("type", "id", "kind", "store") match {
      case Seq(JsString("ItemReturned"), JsNumber(id), JsNumber(kind), JsNumber(store))
           if id.isValidLong && kind.isValidLong && store.isValidLong =>
        (for {
          i <- ItemId(id.longValue)
          k <- CatalogItemId(kind.longValue)
          s <- Store(store.longValue)
        } yield ItemReturned(k, s, i)).fold(e => deserializationError(e.message), identity)
      case _ => deserializationError(msg = "Json format is not valid")
    }

    override def write(itemReturned: ItemReturned): JsValue = JsObject(
      "type" -> "ItemReturned".toJson,
      "id" -> itemReturned.itemId.toJson,
      "kind" -> itemReturned.catalogItemId.toJson,
      "store" -> itemReturned.store.toJson
    )
  }
}
