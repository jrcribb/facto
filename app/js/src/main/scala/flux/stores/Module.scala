package flux.stores

import common.I18n
import common.money.ExchangeRateManager
import common.time.Clock
import flux.action.Dispatcher
import flux.stores.entries._
import models.JsEntityAccess
import models.access.RemoteDatabaseProxy
import models.accounting.config.Config
import models.user.User

final class Module(implicit i18n: I18n,
                   accountingConfig: Config,
                   user: User,
                   remoteDatabaseProxy: RemoteDatabaseProxy,
                   entityAccess: JsEntityAccess,
                   exchangeRateManager: ExchangeRateManager,
                   dispatcher: Dispatcher,
                   clock: Clock) {

  import com.softwaremill.macwire._

  wire[TransactionAndGroupStore]
  wire[BalanceCheckStore]

  implicit private val complexQueryFilter = wire[ComplexQueryFilter]

  implicit val allEntriesStoreFactory = wire[AllEntriesStoreFactory]
  implicit val cashFlowEntriesStoreFactory = wire[CashFlowEntriesStoreFactory]
  implicit val liquidationEntriesStoreFactory = wire[LiquidationEntriesStoreFactory]
  implicit val endowmentEntriesStoreFactory = wire[EndowmentEntriesStoreFactory]
  implicit val tagsStoreFactory = wire[TagsStoreFactory]
  implicit val complexQueryStoreFactory = wire[ComplexQueryStoreFactory]
  implicit val summaryForYearStoreFactory = wire[SummaryForYearStoreFactory]
  implicit val summaryYearsStoreFactory = wire[SummaryYearsStoreFactory]
  implicit val summaryExchangeRateGainsStoreFactory = wire[SummaryExchangeRateGainsStoreFactory]
  implicit val globalMessagesStore = wire[GlobalMessagesStore]
  implicit val pageLoadingStateStore = wire[PageLoadingStateStore]
}
