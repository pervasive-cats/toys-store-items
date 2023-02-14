/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package items.catalogitem.services

import items.catalogitem.{CatalogItemStateHandlers, Repository}
import items.catalogitem.Repository.CatalogItemNotFound
import items.catalogitem.domainevents.{CatalogItemLifted, CatalogItemPutInPlace}
import items.catalogitem.entities.*
import items.catalogitem.valueobjects.*
import items.itemcategory.valueobjects.ItemCategoryId

import com.dimafeng.testcontainers.JdbcDatabaseContainer.CommonParams
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.scalatest.{TestContainerForAll, TestContainersForAll}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import eu.timepit.refined.auto.given
import org.scalatest.EitherValues.given
import org.scalatest.Failed
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers.*
import org.testcontainers.utility.DockerImageName

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class CatalogItemStateHandlersTest extends AnyFunSpec with TestContainerForAll {

  private val timeout: FiniteDuration = 300.seconds

  override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName.parse("postgres:15.1"),
    databaseName = "items",
    username = "test",
    password = "test",
    commonJdbcParams = CommonParams(timeout, timeout, Some("items.sql"))
  )

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var repository: Option[Repository] = None

  @SuppressWarnings(Array("org.wartremover.warts.Var", "scalafix:DisableSyntax.var"))
  private var catalogItem: Option[InPlaceCatalogItem] = None

  override def afterContainersStart(containers: Containers): Unit = {
    repository = Some(
      Repository(
        ConfigFactory
          .load()
          .getConfig("repository")
          .withValue("dataSource.portNumber", ConfigValueFactory.fromAnyRef(containers.container.getFirstMappedPort.intValue()))
      )
    )
    val itemCategoryId: ItemCategoryId = ItemCategoryId(1).getOrElse(fail())
    val store: Store = Store(1).getOrElse(fail())
    val price: Price = Price(Amount(12.99).getOrElse(fail()), Currency.EUR)
    catalogItem = Some(repository.getOrElse(fail()).add(itemCategoryId, store, price).getOrElse(fail()))
  }

  private given Repository = repository.getOrElse(fail())

  describe("The onCatalogItemLifted handler") {
    describe("when is called with an existing catalog item") {
      it("should update the database with the new catalog item state") {
        val db: Repository = repository.getOrElse(fail())
        val item: CatalogItem = catalogItem.getOrElse(fail())
        CatalogItemStateHandlers
          .onCatalogItemLifted(CatalogItemLifted(item.id, item.store))
          .value shouldBe ()
        db.findById(item.id, item.store).value match {
          case i: LiftedCatalogItem => (i.count.value: Long) shouldBe 1L
          case _ => fail()
        }
        CatalogItemStateHandlers
          .onCatalogItemLifted(CatalogItemLifted(item.id, item.store))
          .value shouldBe ()
        db.findById(item.id, item.store).value match {
          case i: LiftedCatalogItem => (i.count.value: Long) shouldBe 2L
          case _ => fail()
        }
      }
    }

    describe("when is called with a non-existing id") {
      it("should not be allowed") {
        val item: CatalogItem = catalogItem.getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(999).getOrElse(fail())
        CatalogItemStateHandlers
          .onCatalogItemLifted(CatalogItemLifted(catalogItemId, item.store))
          .left
          .value shouldBe CatalogItemNotFound
      }
    }

    describe("when is called with a non-existing store") {
      it("should not be allowed") {
        val item: CatalogItem = catalogItem.getOrElse(fail())
        val store: Store = Store(999).getOrElse(fail())
        CatalogItemStateHandlers
          .onCatalogItemLifted(CatalogItemLifted(item.id, store))
          .left
          .value shouldBe CatalogItemNotFound
      }
    }
  }

  describe("The onCatalogItemPutInPlace handler") {
    describe("when is called with an existing catalog item") {
      it("should update the database with the new catalog item state") {
        val item: CatalogItem = catalogItem.getOrElse(fail())
        CatalogItemStateHandlers
          .onCatalogItemPutInPlace(CatalogItemPutInPlace(item.id, item.store))
          .value shouldBe ()
        repository
          .getOrElse(fail())
          .findById(item.id, item.store)
          .value match {
            case i: LiftedCatalogItem => (i.count.value: Long) shouldBe 1L
            case _ => fail()
          }
        CatalogItemStateHandlers
          .onCatalogItemPutInPlace(CatalogItemPutInPlace(item.id, item.store))
          .value shouldBe()
        repository
          .getOrElse(fail())
          .findById(item.id, item.store)
          .value match {
          case _: InPlaceCatalogItem => succeed
          case _ => fail()
        }
      }
    }

    describe("when is called with a non-existing id") {
      it("should not be allowed") {
        val item: CatalogItem = catalogItem.getOrElse(fail())
        val catalogItemId: CatalogItemId = CatalogItemId(999).getOrElse(fail())
        CatalogItemStateHandlers
          .onCatalogItemPutInPlace(CatalogItemPutInPlace(catalogItemId, item.store))
          .left
          .value shouldBe CatalogItemNotFound
      }
    }

    describe("when is called with a non-existing store") {
      it("should not be allowed") {
        val item: CatalogItem = catalogItem.getOrElse(fail())
        val store: Store = Store(999).getOrElse(fail())
        CatalogItemStateHandlers
          .onCatalogItemPutInPlace(CatalogItemPutInPlace(item.id, store))
          .left
          .value shouldBe CatalogItemNotFound
      }
    }
  }
}
