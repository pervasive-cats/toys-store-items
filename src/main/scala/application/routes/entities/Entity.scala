/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package application.routes.entities

import spray.json.DefaultJsonProtocol
import spray.json.JsArray
import spray.json.JsNull
import spray.json.JsObject
import spray.json.JsString
import spray.json.JsValue
import spray.json.JsonFormat
import spray.json.RootJsonFormat
import spray.json.deserializationError
import spray.json.enrichAny
import application.routes.Routes.{DeserializationFailed, RequestFailed}
import application.RequestProcessingFailed
import application.Serializers.given
import items.itemcategory.Repository.ItemCategoryNotFound
import items.RepositoryOperationFailed
import items.catalogitem.valueobjects.Amount.WrongAmountFormat
import items.catalogitem.valueobjects.Count.WrongCountFormat
import items.catalogitem.valueobjects.Store.WrongStoreFormat
import items.catalogitem.Repository.CatalogItemNotFound
import items.itemcategory.valueobjects.Description.WrongDescriptionFormat
import items.itemcategory.valueobjects.Name.WrongNameFormat

import io.github.pervasivecats.items.catalogitem.valueobjects.CatalogItemId.WrongCatalogItemIdFormat
import io.github.pervasivecats.items.item.valueobjects.Customer.WrongCustomerFormat
import io.github.pervasivecats.items.item.valueobjects.ItemId.WrongItemIdFormat
import io.github.pervasivecats.items.item.Repository.{ItemAlreadyPresent, ItemNotFound}
import io.github.pervasivecats.items.itemcategory.valueobjects.ItemCategoryId.WrongItemCategoryIdFormat

trait Entity

object Entity {

  case class ResultResponseEntity[A](result: A) extends Entity

  given [A: JsonFormat]: RootJsonFormat[ResultResponseEntity[A]] with {

    override def read(json: JsValue): ResultResponseEntity[A] = json.asJsObject.getFields("result", "error") match {
      case Seq(result, JsNull) => ResultResponseEntity(result.convertTo[A])
      case _ => deserializationError(msg = "Json format was not valid")
    }

    override def write(response: ResultResponseEntity[A]): JsValue = JsObject(
      "result" -> response.result.toJson,
      "error" -> JsNull
    )
  }

  given [A: JsonFormat]: RootJsonFormat[Validated[A]] with {

    override def read(json: JsValue): Validated[A] = json.asJsObject.getFields("result", "error") match {
      case Seq(JsObject(_), JsNull) => Right[ValidationError, A](json.convertTo[ResultResponseEntity[A]].result)
      case Seq(JsNull, JsObject(_)) => Left[ValidationError, A](json.convertTo[ErrorResponseEntity].error)
    }

    override def write(validated: Validated[A]): JsValue = validated match {
      case Left(error) => ErrorResponseEntity(error).toJson
      case Right(value) => ResultResponseEntity(value).toJson
    }
  }

  case class ErrorResponseEntity(error: ValidationError) extends Entity

  given RootJsonFormat[ErrorResponseEntity] with {

    override def read(json: JsValue): ErrorResponseEntity = json.asJsObject.getFields("result", "error") match {
      case Seq(JsNull, error) =>
        error.asJsObject.getFields("type", "message") match {
          case Seq(JsString(tpe), JsString(message)) =>
            ErrorResponseEntity(tpe match {
              case "ItemCategoryNotFound" => ItemCategoryNotFound
              case "CatalogItemNotFound" => CatalogItemNotFound
              case "ItemNotFound" => ItemNotFound
              case "ItemAlreadyPresent" => ItemAlreadyPresent
              case "WrongNameFormat" => WrongNameFormat
              case "WrongDescriptionFormat" => WrongDescriptionFormat
              case "WrongAmountFormat" => WrongAmountFormat
              case "WrongCountFormat" => WrongCountFormat
              case "WrongStoreFormat" => WrongStoreFormat
              case "WrongCustomerFormat" => WrongCustomerFormat
              case "WrongItemCategoryIdFormat" => WrongItemCategoryIdFormat
              case "WrongCatalogItemIdFormat" => WrongCatalogItemIdFormat
              case "WrongItemIdFormat" => WrongItemIdFormat
              case "RepositoryOperationFailed" => RepositoryOperationFailed
              case "RequestProcessingFailed" => RequestProcessingFailed
              case "RequestFailed" => RequestFailed
              case "DeserializationFailed" => DeserializationFailed(message)
              case _ => deserializationError(msg = "Json format was not valid")
            })
          case _ => deserializationError(msg = "Json format was not valid")
        }
      case _ => deserializationError(msg = "Json format was not valid")
    }

    override def write(response: ErrorResponseEntity): JsValue = JsObject(
      "result" -> JsNull,
      "error" -> JsObject(
        "type" -> response.error.getClass.getSimpleName.replace("$", "").toJson,
        "message" -> response.error.message.toJson
      )
    )
  }
}
