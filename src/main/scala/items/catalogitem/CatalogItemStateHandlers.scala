/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem

import items.catalogitem.domainevents.{CatalogItemLifted, CatalogItemPutInPlace}
import items.catalogitem.Repository
import items.catalogitem.entities.{CatalogItem, InPlaceCatalogItem, LiftedCatalogItem}
import items.catalogitem.entities.LiftedCatalogItemOps.putInPlace
import items.catalogitem.entities.CatalogItemOps.lift

trait CatalogItemStateHandlers {

  def onCatalogItemPutInPlace(event: CatalogItemPutInPlace)(using Repository): Validated[Unit]

  def onCatalogItemLifted(event: CatalogItemLifted)(using Repository): Validated[Unit]
}

object CatalogItemStateHandlers extends CatalogItemStateHandlers {

  case object EventRejected extends ValidationError {

    override val message: String = "The event was rejected because it could not be processed"
  }

  override def onCatalogItemPutInPlace(event: CatalogItemPutInPlace)(using Repository): Validated[Unit] =
    for {
      c <- summon[Repository].findById(event.catalogItemId, event.store)
      _ <- c match {
        case _: InPlaceCatalogItem =>
          Left[ValidationError, CatalogItem](EventRejected)
        case i: LiftedCatalogItem =>
          i.putInPlace.map {
            case it: InPlaceCatalogItem => summon[Repository].update(it, None, it.price)
            case it: LiftedCatalogItem => summon[Repository].update(it, Some(it.count), it.price)
          }
      }
    } yield ()

  override def onCatalogItemLifted(event: CatalogItemLifted)(using Repository): Validated[Unit] =
    for {
      c <- summon[Repository].findById(event.catalogItemId, event.store)
      l <- c match {
        case i: InPlaceCatalogItem => i.lift
        case i: LiftedCatalogItem => i.lift
      }
      _ <- summon[Repository].update(l, Some(l.count), l.price)
    } yield ()
}
