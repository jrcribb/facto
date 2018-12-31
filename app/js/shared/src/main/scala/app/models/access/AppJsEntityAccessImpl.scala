package app.models.access

import app.models.user.User
import hydro.models.access.RemoteDatabaseProxy
import scala.collection.immutable.Seq
import app.api.ScalaJsApi.GetInitialDataResponse
import app.api.ScalaJsApiClient
import hydro.models.access.LocalDatabaseImpl.SecondaryIndexFunction
import app.models.accounting.BalanceCheck
import app.models.money.ExchangeRateMeasurement
import app.models.accounting.TransactionGroup
import app.models.accounting.Transaction

import app.models.user.User
import hydro.models.access.EntityModificationPushClientFactory
import hydro.models.access.HybridRemoteDatabaseProxy
import hydro.models.access.JsEntityAccess
import hydro.models.access.JsEntityAccessImpl
import hydro.models.access.LocalDatabaseImpl
import hydro.models.access.LocalDatabaseImpl.SecondaryIndexFunction
import hydro.models.access.DbResultSet
import hydro.models.access.DbQueryExecutor

import scala.collection.immutable.Seq

private[access] final class AppJsEntityAccessImpl(allUsers: Seq[User])(
    implicit remoteDatabaseProxy: RemoteDatabaseProxy,
    entityModificationPushClientFactory: EntityModificationPushClientFactory)
    extends JsEntityAccessImpl
    with AppJsEntityAccess {

  override def newQuerySyncForUser() =
    DbResultSet.fromExecutor(DbQueryExecutor.fromEntities(allUsers))
}
