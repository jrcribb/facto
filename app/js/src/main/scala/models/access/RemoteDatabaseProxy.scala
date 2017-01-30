package models.access

import scala.async.Async.{async, await}
import scala.concurrent.duration._
import scala.scalajs.js
import api.ScalaJsApiClient
import common.LoggingUtils.logExceptions
import common.ScalaUtils.visibleForTesting
import jsfacades.Loki
import models.access.SingletonKey._
import models.manager.{Entity, EntityModification, EntityType}

import scala.collection.immutable.Seq
import scala.collection.mutable
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala2js.Converters._

trait RemoteDatabaseProxy {

  // **************** Getters ****************//
  def newQuery[E <: Entity : EntityType](): Loki.ResultSet[E]
  /** Returns true if there are local pending `Add` modifications for the given entity. Note that only its id is used. */
  def hasLocalAddModifications[E <: Entity : EntityType](entity: E): Boolean

  // **************** Setters ****************//
  def persistModifications(modifications: Seq[EntityModification]): Future[Unit]
  def clearLocalDatabase(): Future[Unit]

  // **************** Other ****************//
  def registerListener(listener: RemoteDatabaseProxy.Listener): Unit
  private[access] def startSchedulingModifiedEntityUpdates(): Unit
}

object RemoteDatabaseProxy {

  private val localDatabaseAndEntityVersion = "1.0"

  trait Listener {
    /**
      * Called when the local database was updated with a modification due to a local change request. This change is not
      * yet persisted in the remote database.
      */
    def addedLocally(modifications: Seq[EntityModification]): Unit

    /**
      * Called when a remote entity was changed, either due to a change request from this or another client.
      *
      * Note that a preceding `addedLocally()` call may have been made earlier for the same modifications, but this is
      * not always the case.
      */
    def localModificationPersistedRemotely(modifications: Seq[EntityModification]): Unit = {}

    /**
      *
      */
    def addedRemotely(modifications: Seq[EntityModification]): Unit

    /**
      * Called after the initial loading or reloading of the database. This also gets called when the database is
      * cleared.
      */
    def loadedDatabase(): Unit
  }

  private[access] final class Impl(apiClient: ScalaJsApiClient,
                                   possiblyEmptyLocalDatabase: Future[LocalDatabase]) extends RemoteDatabaseProxy {

    private val localDatabase: Future[LocalDatabase] = possiblyEmptyLocalDatabase flatMap toValidDatabase
    private var listeners: Seq[Listener] = Seq()
    private val localAddModificationIds: Map[EntityType.any, mutable.Set[Long]] =
      EntityType.values.map(t => t -> mutable.Set[Long]()).toMap
    private var isCallingListeners: Boolean = false

    @visibleForTesting private[access] val completelyLoaded: Future[_] = async {
      await(localDatabase)
      await(invokeListenersAsync(_.loadedDatabase()))
    }

    // **************** Getters ****************//
    override def newQuery[E <: Entity : EntityType](): Loki.ResultSet[E] = {
      val maybeDb = localDatabase.value.flatMap(_.toOption)
      maybeDb.map(_.newQuery[E]()) getOrElse Loki.ResultSet.empty[E]
    }

    override def hasLocalAddModifications[E <: Entity : EntityType](entity: E): Boolean = {
      localAddModificationIds(implicitly[EntityType[E]]) contains entity.id
    }

    // **************** Setters ****************//
    override def persistModifications(modifications: Seq[EntityModification]): Future[Unit] = async {
      require(!isCallingListeners)

      val db = await(localDatabase)
      db.applyModifications(modifications)

      for {
        modification <- modifications
        if modification.isInstanceOf[EntityModification.Add[_]]
      } localAddModificationIds(modification.entityType) += modification.entityId
      val listeners1 = invokeListenersAsync(_.addedLocally(modifications))

      await(apiClient.persistEntityModifications(modifications))

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

      val db = await(localDatabase)
      await(db.clear())

      await(invokeListenersAsync(_.loadedDatabase()))
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

    private def toValidDatabase(db: LocalDatabase): Future[LocalDatabase] = async {
      if (db.isEmpty() || !db.getSingletonValue(VersionKey).contains(localDatabaseAndEntityVersion)) {
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
        db
      } else {
        db
      }
    }

    @visibleForTesting private[access] def updateModifiedEntities(): Future[Unit] = async {
      val db = await(localDatabase)
      val response = await(apiClient.getEntityModifications(db.getSingletonValue(NextUpdateTokenKey).get))
      db.setSingletonValue(NextUpdateTokenKey, response.nextUpdateToken)
      if (response.modifications.nonEmpty) {
        db.applyModifications(response.modifications)
        await(invokeListenersAsync(_.addedRemotely(response.modifications)))
      }
    }
  }
}
