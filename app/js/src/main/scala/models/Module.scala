package models

import api.ScalaJsApi.GetInitialDataResponse
import common.money.ExchangeRateManager
import models.access.RemoteDatabaseProxy
import models.money.{JsExchangeRateManager, JsExchangeRateMeasurementManager}

final class Module(implicit remoteDatabaseProxy: RemoteDatabaseProxy,
                   getInitialDataResponse: GetInitialDataResponse) {

  import com.softwaremill.macwire._

  implicit lazy val exchangeRateMeasurementManager = wire[JsExchangeRateMeasurementManager]

  implicit lazy val entityAccess: JsEntityAccess = wire[JsEntityAccess]
  implicit lazy val exchangeRateManager: ExchangeRateManager =
    new JsExchangeRateManager(getInitialDataResponse.ratioReferenceToForeignCurrency)
}
