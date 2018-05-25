package models.access

import common.ScalaUtils.visibleForTesting
import models.Entity
import models.modification.{EntityModification, EntityType}

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala2js.Converters._

/** Client-side persistence layer. */
@visibleForTesting
trait LocalDatabase {
  // **************** Getters ****************//
  def queryExecutor[E <: Entity: EntityType](): DbQueryExecutor.Async[E]
  def pendingModifications(): Future[Seq[EntityModification]]
  def getSingletonValue[V](key: SingletonKey[V]): Future[Option[V]]
  def isEmpty: Future[Boolean]

  // **************** Setters ****************//
  /**
    * Applies given modification in memory but doesn't persist it in the browser's storage (call `save()` to do this).
    *
    * @return true if the in memory database changed as a result of this method
    */
  def applyModifications(modifications: Seq[EntityModification]): Future[Boolean]
  def addAll[E <: Entity: EntityType](entities: Seq[E]): Future[Unit]

  def addPendingModifications(modifications: Seq[EntityModification]): Future[Unit]
  def removePendingModifications(modifications: Seq[EntityModification]): Future[Unit]

  /** Sets given singleton value in memory but doesn't persist it in the browser's storage (call `save()` to do this). */
  def setSingletonValue[V](key: SingletonKey[V], value: V): Future[Unit]

  /** Persists all previously made changes to the browser's storage. */
  def save(): Future[Unit]

  /** Removes all data and resets its configuration. */
  def resetAndInitialize(): Future[Unit]
}
