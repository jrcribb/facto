package models.access

import api.ScalaJsApi.GetInitialDataResponse
import api.ScalaJsApiClient
import common.time.Clock
import models.user.User

import scala.concurrent.Future

final class Module(implicit user: User,
                   scalaJsApiClient: ScalaJsApiClient,
                   clock: Clock,
                   getInitialDataResponse: GetInitialDataResponse) {

  implicit val entityAccess: JsEntityAccess = {
    val webWorkerModule = new models.access.webworker.Module()
    implicit val localDatabaseWebWorkerApiStub = webWorkerModule.localDatabaseWebWorkerApiStub
    val localDatabaseFuture = LocalDatabase.create(encryptionSecret = user.databaseEncryptionKey)
    implicit val remoteDatabaseProxy = HybridRemoteDatabaseProxy.create(localDatabaseFuture)
    val entityAccess = new JsEntityAccessImpl(getInitialDataResponse.allUsers)

    entityAccess.startSchedulingModifiedEntityUpdates()

    entityAccess
  }
}
