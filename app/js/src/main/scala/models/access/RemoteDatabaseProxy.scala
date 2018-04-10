package models.access

import api.ScalaJsApi.UpdateToken
import models.Entity
import models.modification.{EntityModification, EntityType}

import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala2js.Converters._

trait RemoteDatabaseProxy {
  def queryExecutor[E <: Entity: EntityType](): DbQueryExecutor.Async[E]

  def persistEntityModifications(modifications: Seq[EntityModification]): Future[Unit]

  def getAndApplyRemotelyModifiedEntities(
      updateToken: Option[UpdateToken]): Future[GetRemotelyModifiedEntitiesResponse]

  case class GetRemotelyModifiedEntitiesResponse(changes: Seq[EntityModification],
                                                 nextUpdateToken: UpdateToken)
}