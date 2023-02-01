/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.item.services

import items.item.domainevents.{ItemAddedToCart, ItemPutInPlace, ItemReturned}

trait ItemStateHandlers {

  def onItemAddedToCart(event: ItemAddedToCart): Unit

  def onItemReturned(event: ItemReturned): Unit

  def onItemPutInPlace(event: ItemPutInPlace): Unit
}
