package app.flux.stores.entries.factories

import app.common.accounting.ComplexQueryFilter
import app.common.money.CurrencyValueManager
import app.common.money.ReferenceMoney
import app.common.time.AccountingYear
import app.common.time.DatedMonth
import app.flux.stores.entries.EntriesStore
import app.flux.stores.entries.factories.SummaryForYearStoreFactory.SummaryForYear
import app.models.access.AppDbQuerySorting
import app.models.access.AppJsEntityAccess
import app.models.access.ModelFields
import app.models.accounting.BalanceCheck
import app.models.accounting.Transaction
import app.models.accounting.config.Account
import app.models.accounting.config.Category
import app.models.accounting.config.Config
import hydro.common.time.LocalDateTime
import hydro.models.access.DbQueryImplicits._
import hydro.models.access.DbQuery
import hydro.models.access.ModelField

import scala.async.Async.async
import scala.async.Async.await
import scala.collection.immutable.Seq
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

final class SummaryForYearStoreFactory(implicit
    entityAccess: AppJsEntityAccess,
    accountingConfig: Config,
    complexQueryFilter: ComplexQueryFilter,
) extends EntriesStoreFactory[SummaryForYear] {

  // **************** Public API ****************//
  def get(account: Account, year: AccountingYear, query: String = ""): Store =
    get(Input(account = account, year = year, query = query))

  // **************** Implementation of EntriesStoreFactory methods/types ****************//
  override protected def createNew(input: Input) = new Store {
    private val combinedFilter: DbQuery.Filter[Transaction] =
      (ModelFields.Transaction.beneficiaryAccountCode === input.account.code) &&
        filterInYear(ModelFields.Transaction.consumedDate, input.year) &&
        complexQueryFilter.fromQuery(input.query)

    override protected def calculateState() = async {
      val transactions: Seq[Transaction] =
        await(
          entityAccess
            .newQuery[Transaction]()
            .filter(combinedFilter)
            .sort(AppDbQuerySorting.Transaction.deterministicallyByConsumedDate)
            .data()
        )

      SummaryForYear(transactions)
    }

    override protected def transactionUpsertImpactsState(transaction: Transaction, state: State) =
      combinedFilter(transaction)
    override protected def balanceCheckUpsertImpactsState(balanceCheck: BalanceCheck, state: State) =
      false
  }

  /* override */
  protected case class Input(account: Account, year: AccountingYear, query: String = "")

  // **************** Private helper methods ****************//
  private def filterInYear[E](
      field: ModelField[LocalDateTime, E],
      year: AccountingYear,
  ): DbQuery.Filter[E] = {
    val months = DatedMonth.allMonthsIn(year)
    field >= months.head.startTime && field < months.last.startTimeOfNextMonth
  }
}

object SummaryForYearStoreFactory {
  case class SummaryForYear(private val transactions: Seq[Transaction])(implicit accountingConfig: Config)
      extends EntriesStore.StateTrait {
    private val cells: Map[Category, Map[DatedMonth, SummaryCell]] =
      transactions
        .groupBy(_.category)
        .mapValues(_.groupBy(t => DatedMonth.containing(t.consumedDate)).mapValues(SummaryCell.apply))

    /** All months for which there is at least one transaction. */
    val months: Set[DatedMonth] =
      transactions.toStream.map(t => DatedMonth.containing(t.consumedDate)).toSet

    /** All categories for which there is at least one transaction. */
    def categories: Set[Category] = cells.keySet

    def cell(category: Category, month: DatedMonth): SummaryCell =
      cells.get(category).flatMap(_.get(month)) getOrElse SummaryCell.empty

    override protected val impactingTransactionIds = transactions.toStream.map(_.id).toSet
    override protected def impactingBalanceCheckIds = Set()
  }

  object SummaryForYear {
    def empty(implicit accountingConfig: Config): SummaryForYear = SummaryForYear(Seq())
  }

  case class SummaryCell(transactions: Seq[Transaction]) {
    private var _totalFlow: ReferenceMoney = _
    private var _totalFlowCorrectedForInflation: ReferenceMoney = _

    def nonEmpty: Boolean = transactions.nonEmpty

    def totalFlow(correctForInflation: Boolean)(implicit
        currencyValueManager: CurrencyValueManager,
        accountingConfig: Config,
    ): ReferenceMoney = {
      if (correctForInflation) {
        if (_totalFlowCorrectedForInflation eq null) {
          _totalFlowCorrectedForInflation =
            transactions.map(_.flow.exchangedForReferenceCurrency(correctForInflation = true)).sum
        }
        _totalFlowCorrectedForInflation
      } else {
        if (_totalFlow eq null) {
          _totalFlow = transactions.map(_.flow.exchangedForReferenceCurrency()).sum
        }
        _totalFlow
      }
    }
  }
  object SummaryCell {
    val empty: SummaryCell = SummaryCell(Seq())
  }
}
