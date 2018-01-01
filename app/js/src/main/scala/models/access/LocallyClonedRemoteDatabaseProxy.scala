package models.access

import api.ScalaJsApiClient
import common.LoggingUtils.logExceptions
import common.ScalaUtils.visibleForTesting
import models.Entity
import models.access.RemoteDatabaseProxy.Listener
import models.access.SingletonKey.{NextUpdateTokenKey, VersionKey}
import models.modification.{EntityModification, EntityType}

import scala.async.Async.{async, await}
import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

private[access] final class LocallyClonedRemoteDatabaseProxy(apiClient: ScalaJsApiClient,
                                                             localDatabase: LocalDatabase)
    extends RemoteDatabaseProxy {

  private var listeners: Seq[Listener] = Seq()
  private val localAddModificationIds: Map[EntityType.any, mutable.Set[Long]] =
    EntityType.values.map(t => t -> mutable.Set[Long]()).toMap
  private var isCallingListeners: Boolean = false

  // **************** Getters ****************//
  override def newQuery[E <: Entity: EntityType](): DbResultSet.Async[E] = {
//    localDatabase.newQuery[E]()
    ???
  }

  override def hasLocalAddModifications[E <: Entity: EntityType](entity: E): Boolean = {
    localAddModificationIds(implicitly[EntityType[E]]) contains entity.id
  }

  // **************** Setters ****************//
  override def persistModifications(modifications: Seq[EntityModification]): Future[Unit] = async {
    require(!isCallingListeners)

    localDatabase.applyModifications(modifications)
    val saveFuture = localDatabase.save()

    for {
      modification <- modifications
      if modification.isInstanceOf[EntityModification.Add[_]]
    } localAddModificationIds(modification.entityType) += modification.entityId
    val listeners1 = invokeListenersAsync(_.addedLocally(modifications))

    val apiFuture = apiClient.persistEntityModifications(modifications)

    await(saveFuture)
    await(apiFuture)

    for {
      modification <- modifications
      if modification.isInstanceOf[EntityModification.Add[_]]
    } localAddModificationIds(modification.entityType) -= modification.entityId
    val listeners2 = invokeListenersAsync(_.localModificationPersistedRemotely(modifications))

    await(listeners1)
    await(listeners2)
  }

  override def clearLocalDatabase(): Future[Unit] = async {
    require(!isCallingListeners)

    await(localDatabase.clear())
  }

  // **************** Other ****************//
  override def registerListener(listener: Listener): Unit = {
    require(!isCallingListeners)

    listeners = listeners :+ listener
  }

  override private[access] def startSchedulingModifiedEntityUpdates(): Unit = {
    var timeout = 5.seconds
    def cyclicLogic(): Unit = {
      updateModifiedEntities() onComplete { _ =>
        js.timers.setTimeout(timeout)(cyclicLogic)
        timeout * 1.02
      }
    }

    js.timers.setTimeout(0)(cyclicLogic)
  }

  // **************** Private helper methods ****************//
  private def invokeListenersAsync(func: Listener => Unit): Future[Unit] = {
    Future {
      logExceptions {
        require(!isCallingListeners)
        isCallingListeners = true
        listeners.foreach(func)
        isCallingListeners = false
      }
    }
  }

  @visibleForTesting private[access] def updateModifiedEntities(): Future[Unit] = async {
    val response =
      await(apiClient.getEntityModifications(localDatabase.getSingletonValue(NextUpdateTokenKey).get))
    if (response.modifications.nonEmpty) {
      println(s"  ${response.modifications.size} remote modifications received")
      val somethingChanged = localDatabase.applyModifications(response.modifications)
      localDatabase.setSingletonValue(NextUpdateTokenKey, response.nextUpdateToken)
      await(localDatabase.save())

      if (somethingChanged) {
        await(invokeListenersAsync(_.addedRemotely(response.modifications)))
      }
    }
  }
}

private[access] object LocallyClonedRemoteDatabaseProxy {
  private val localDatabaseAndEntityVersion = "1.0"

  private[access] def create(apiClient: ScalaJsApiClient,
                             possiblyEmptyLocalDatabase: LocalDatabase): Future[RemoteDatabaseProxy] =
    async {
      val db = possiblyEmptyLocalDatabase
      val populatedDb = {
        val populateIsNecessary = {
          if (db.isEmpty) {
            println(s"  Database is empty")
            true
          } else if (!db.getSingletonValue(VersionKey).contains(localDatabaseAndEntityVersion)) {
            println(
              s"  The database version ${db.getSingletonValue(VersionKey) getOrElse "<empty>"} no longer matches " +
                s"the newest version $localDatabaseAndEntityVersion")
            true
          } else {
            println(s"  Database was loaded successfully. No need for a full repopulation.")
            false
          }
        }
        if (populateIsNecessary) {
          println(s"  Populating database...")

          // Reset database
          await(db.clear())

          // Set version
          db.setSingletonValue(VersionKey, localDatabaseAndEntityVersion)

          // Add all entities
          val allEntitiesResponse = await(apiClient.getAllEntities(EntityType.values))
          for (entityType <- allEntitiesResponse.entityTypes) {
            def addAllToDb[E <: Entity](implicit entityType: EntityType[E]) =
              db.addAll(allEntitiesResponse.entities(entityType))
            addAllToDb(entityType)
          }
          db.setSingletonValue(NextUpdateTokenKey, allEntitiesResponse.nextUpdateToken)

          // Await because we don't want to save unpersisted modifications that can be made as soon as
          // the database becomes valid.
          await(db.save())
          println(s"  Population done!")
          db
        } else {
          db
        }
      }
      new LocallyClonedRemoteDatabaseProxy(apiClient, populatedDb)
    }

}
