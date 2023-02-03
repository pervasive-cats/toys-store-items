/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.entities

import java.util.Objects

import io.github.pervasivecats.items.Validated
import io.github.pervasivecats.items.catalogitem.entities.CatalogItem
import io.github.pervasivecats.items.item.entities.Item.itemEquals

import io.getquill.metaprog.Extractors.ConstantValue.Kind

import items.item.valueobjects.{Customer, ItemId}

trait InCartItem extends Item {

  val customer: Customer
}

object InCartItem {

  private case class InCartItemImpl(id: ItemId, kind: CatalogItem, customer: Customer) extends InCartItem {

    override def equals(obj: Any): Boolean = itemEquals(obj)(id)

    override def hashCode(): Int = Objects.hash(id)
  }

  given InCartItemOps[InCartItem] with {

    override def returnToStore(inCartItem: InCartItem): ReturnedItem = ReturnedItem(inCartItem.id, inCartItem.kind)
  }

  def apply(id: ItemId, kind: CatalogItem, customer: Customer): InCartItem = InCartItemImpl(id, kind, customer)
}
