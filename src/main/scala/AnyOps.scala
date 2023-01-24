package io.github.pervasivecats

import scala.annotation.targetName

object AnyOps {

  extension[A] (self: A) {

    @targetName("equals")
    @SuppressWarnings(Array("org.wartremover.warts.Equals", "scalafix:DisableSyntax.=="))
    def ===(other: A): Boolean = self == other

    @targetName("notEquals")
    def !==(other: A): Boolean = !(self === other)
  }
}
