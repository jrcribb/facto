package app.flux.stores.entries.factories

import app.common.time.AccountingYear
import app.common.time.YearRange
import app.flux.stores.entries.EntriesStore
import app.flux.stores.entries.factories.SummaryYearsStoreFactory.State
import app.models.access.AppDbQuerySorting
import app.models.access.AppJsEntityAccess
import app.models.access.ModelFields
import app.models.accounting.BalanceCheck
import app.models.accounting.Transaction
import app.models.accounting.config.Account
import app.models.accounting.config.Config
import hydro.models.access.DbQuery
import hydro.models.access.DbQueryImplicits._

import scala.async.Async.async
import scala.async.Async.await
import scala.collection.immutable.Seq
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
 * Store factory that calculates the year span of all transactions of an account.
 *
 * The calculated range is guaranteed to contain at least all years there are transactions for but may also contain
 * more (although unlikely).
 */
final class SummaryYearsStoreFactory(implicit entityAccess: AppJsEntityAccess, accountingConfig: Config)
    extends EntriesStoreFactory[State] {

  // **************** Implementation of EntriesStoreFactory methods/types ****************//
  override protected def createNew(account: Account) = new Store {
    override protected def calculateState() = async {
      val earliestFuture = getFirstAfterSorting(AppDbQuerySorting.Transaction.deterministicallyByConsumedDate)
      val latestFuture =
        getFirstAfterSorting(AppDbQuerySorting.Transaction.deterministicallyByConsumedDate.reversed)
      val (maybeEarliest, maybeLatest) = (await(earliestFuture), await(latestFuture))

      val rangeOption = for {
        earliest <- maybeEarliest
        latest <- maybeLatest
      } yield State(
        YearRange
          .closed(AccountingYear.from(earliest.consumedDate), AccountingYear.from(latest.consumedDate)),
        impactingTransactionIds = Set(earliest.id, latest.id),
      )

      rangeOption getOrElse State.empty
    }

    override protected def transactionUpsertImpactsState(transaction: Transaction, oldState: State) =
      transaction.beneficiaryAccountCode == account.code && !oldState.yearRange.contains(
        AccountingYear.from(transaction.consumedDate)
      )
    override protected def balanceCheckUpsertImpactsState(balanceCheck: BalanceCheck, state: State) = false

    private def getFirstAfterSorting(sorting: DbQuery.Sorting[Transaction]): Future[Option[Transaction]] =
      async {
        val data = await(
          entityAccess
            .newQuery[Transaction]()
            .filter(ModelFields.Transaction.beneficiaryAccountCode === account.code)
            .sort(sorting)
            .limit(1)
            .data()
        )
        data match {
          case Seq()  => None
          case Seq(t) => Some(t)
        }
      }
  }

  /* override */
  protected type Input = Account
}

object SummaryYearsStoreFactory {
  case class State(yearRange: YearRange, protected override val impactingTransactionIds: Set[Long])
      extends EntriesStore.StateTrait {
    protected override def impactingBalanceCheckIds = Set()
  }
  object State {
    def empty: State = State(YearRange.empty, impactingTransactionIds = Set())
  }
}
