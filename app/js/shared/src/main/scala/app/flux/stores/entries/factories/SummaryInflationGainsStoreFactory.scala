package app.flux.stores.entries.factories

import app.common.accounting.ComplexQueryFilter
import app.common.accounting.DateToBalanceFunction
import app.common.money.CurrencyValueManager
import app.common.money.MoneyWithGeneralCurrency
import app.common.money.ReferenceMoney
import app.common.time.AccountingYear
import app.common.time.DatedMonth
import app.flux.stores.entries.factories.SummaryInflationGainsStoreFactory.GainsForMonth
import app.flux.stores.entries.factories.SummaryInflationGainsStoreFactory.InflationGains
import app.flux.stores.entries.AccountingEntryUtils
import app.flux.stores.entries.AccountingEntryUtils.GainFromMoneyFunction
import app.flux.stores.entries.EntriesStore
import app.models.access.AppDbQuerySorting
import app.models.access.AppJsEntityAccess
import app.models.access.ModelFields
import app.models.accounting.BalanceCheck
import app.models.accounting.Transaction
import app.models.accounting.config.Account
import app.models.accounting.config.Config
import app.models.accounting.config.MoneyReservoir
import hydro.common.time.Clock
import hydro.common.time.JavaTimeImplicits._
import hydro.common.time.LocalDateTime
import hydro.models.access.DbQuery
import hydro.models.access.DbQueryImplicits._
import hydro.models.access.ModelField
import hydro.models.Entity

import scala.async.Async.async
import scala.async.Async.await
import scala.collection.immutable.Seq
import scala.collection.immutable.SortedMap
import scala.collection.immutable.SortedSet
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Store factory that calculates the monthly gains and losses made by inflation. */
final class SummaryInflationGainsStoreFactory(implicit
    entityAccess: AppJsEntityAccess,
    currencyValueManager: CurrencyValueManager,
    accountingConfig: Config,
    complexQueryFilter: ComplexQueryFilter,
    clock: Clock,
    accountingEntryUtils: AccountingEntryUtils,
) extends EntriesStoreFactory[InflationGains] {

  // **************** Public API ****************//
  def get(account: Account = null, year: AccountingYear = null): Store = {
    get(Input(account = Option(account), year = Option(year)))
  }

  // **************** Implementation of EntriesStoreFactory methods/types ****************//
  override protected def createNew(input: Input) = new Store {
    override protected def calculateState() = async {
      InflationGains.sum {
        val futures = for {
          reservoir <- accountingConfig.moneyReservoirs(includeHidden = true)
          if isRelevantReservoir(reservoir)
        } yield calculateInflationGains(reservoir)

        await(Future.sequence(futures))
      }
    }

    override protected def transactionUpsertImpactsState(transaction: Transaction, state: State) = {
      isRelevantReservoir(transaction.moneyReservoir) &&
      (input.year.isEmpty || AccountingYear.from(transaction.transactionDate) <= input.year.get)
    }
    override protected def balanceCheckUpsertImpactsState(balanceCheck: BalanceCheck, state: State) = {
      isRelevantReservoir(balanceCheck.moneyReservoir) &&
      (input.year.isEmpty || AccountingYear.from(balanceCheck.checkDate) <= input.year.get)
    }

    // **************** Private helper methods ****************//
    private def calculateInflationGains(reservoir: MoneyReservoir): Future[InflationGains] = {
      if (reservoir.assumeThisFollowsInflationUntilNextBalanceCheck) {
        calculateInflationGainsAtCheckTimes(reservoir)
      } else {
        calculateRegularInflationGains(reservoir)
      }
    }

    private def calculateRegularInflationGains(reservoir: MoneyReservoir): Future[InflationGains] = async {
      val transactionsAndBalanceChecks =
        await(
          accountingEntryUtils.getTransactionsAndBalanceChecks(
            reservoir = reservoir,
            yearFilter = input.year,
          )
        )

      val monthsInPeriod: Seq[DatedMonth] = input.year match {
        case Some(year) => DatedMonth.allMonthsIn(year)
        case None       => transactionsAndBalanceChecks.monthsCoveredByEntriesUpUntilToday
      }

      InflationGains(
        monthToGains = monthsInPeriod.map { month =>
          val gain = transactionsAndBalanceChecks.calculateGainsInMonth(
            month,
            GainFromMoneyFunction.GainsFromInflation(),
          )
          month -> GainsForMonth.forSingle(reservoir, gain)
        }.toMap,
        impactingTransactionIds = transactionsAndBalanceChecks.impactingTransactionIds,
        impactingBalanceCheckIds = transactionsAndBalanceChecks.impactingBalanceCheckIds,
      )
    }

    private def calculateInflationGainsAtCheckTimes(
        reservoir: MoneyReservoir
    ): Future[InflationGains] = async {
      val transactionsAndBalanceChecks =
        await(
          accountingEntryUtils.getTransactionsAndBalanceChecks(
            reservoir = reservoir,
            // Always load all entries before the current year. This is not optimal, but simplifies the code.
            oldestRelevantBalanceCheck = None,
            upperBoundDateTime = input.year.map(y => DatedMonth.allMonthsIn(y).last.startTimeOfNextMonth),
          )
        )

      val monthsInPeriod: Seq[DatedMonth] = input.year match {
        case Some(year) => DatedMonth.allMonthsIn(year)
        case None       => transactionsAndBalanceChecks.monthsCoveredByEntriesUpUntilToday
      }

      val balanceCheckDates: SortedSet[LocalDateTime] = SortedSet(
        transactionsAndBalanceChecks.balanceChecks.map(_.checkDate): _*
      )

      val dateToBalanceFunction = transactionsAndBalanceChecks.getCachedDateToBalanceFunction()

      InflationGains(
        monthToGains = monthsInPeriod.map { month =>
          val gain = {
            val result =
              for {
                endDate <- balanceCheckDates.from(month.startTime).to(month.startTimeOfNextMonth).lastOption
              } yield {
                val startDate = balanceCheckDates.to(month.startTime).lastOption getOrElse LocalDateTime.MIN

                val gainFromInitialMoney = GainFromMoneyFunction
                  .GainsFromInflation()
                  .apply(
                    startDate = startDate,
                    endDate = endDate,
                    amount = dateToBalanceFunction(startDate),
                  )

                val gainFromUpdates =
                  dateToBalanceFunction
                    .updatesInRange(startDate, endDate)
                    .map { case (date, DateToBalanceFunction.Update(balance, changeComparedToLast)) =>
                      GainFromMoneyFunction
                        .GainsFromInflation()
                        .apply(
                          startDate = date,
                          endDate = endDate,
                          amount = changeComparedToLast,
                        )
                    }
                    .sum
                gainFromInitialMoney + gainFromUpdates
              }

            result getOrElse ReferenceMoney(0)
          }

          month -> GainsForMonth.forSingle(reservoir, gain)
        }.toMap,
        impactingTransactionIds = transactionsAndBalanceChecks.impactingTransactionIds,
        impactingBalanceCheckIds = transactionsAndBalanceChecks.impactingBalanceCheckIds,
      )
    }

    private def isRelevantReservoir(reservoir: MoneyReservoir): Boolean = {
      isRelevantAccount(reservoir.owner)
    }

    private def isRelevantAccount(account: Account): Boolean = {
      input.account match {
        case None    => true
        case Some(a) => account == a
      }
    }
  }

  /* override */
  protected case class Input(account: Option[Account], year: Option[AccountingYear])
}

object SummaryInflationGainsStoreFactory {

  private def combineMapValues[K, V](maps: Seq[Map[K, V]])(valueCombiner: Seq[V] => V): Map[K, V] = {
    val keys = maps.map(_.keySet).reduceOption(_ union _) getOrElse Set()
    keys.map(key => key -> valueCombiner(maps.filter(_ contains key).map(_.apply(key)))).toMap
  }

  case class InflationGains(
      monthToGains: Map[DatedMonth, GainsForMonth],
      protected override val impactingTransactionIds: Set[Long],
      protected override val impactingBalanceCheckIds: Set[Long],
  ) extends EntriesStore.StateTrait {
    def gainsForMonth(month: DatedMonth): GainsForMonth = monthToGains.getOrElse(month, GainsForMonth.empty)
    def nonEmpty: Boolean = this != InflationGains.empty
  }

  object InflationGains {
    val empty: InflationGains = InflationGains(Map(), Set(), Set())

    def sum(gains: Seq[InflationGains]): InflationGains = InflationGains(
      monthToGains = combineMapValues(gains.map(_.monthToGains))(GainsForMonth.sum),
      impactingTransactionIds = gains.map(_.impactingTransactionIds).reduceOption(_ union _) getOrElse Set(),
      impactingBalanceCheckIds = gains.map(_.impactingBalanceCheckIds).reduceOption(_ union _) getOrElse Set(),
    )
  }

  case class GainsForMonth private (reservoirToGains: Map[MoneyReservoir, ReferenceMoney]) {
    reservoirToGains.values.foreach(gain => require(!gain.isZero))

    lazy val total: ReferenceMoney = reservoirToGains.values.sum
    def nonEmpty: Boolean = reservoirToGains.nonEmpty
  }
  object GainsForMonth {
    val empty: GainsForMonth = GainsForMonth(Map())

    def forSingle(reservoir: MoneyReservoir, gain: ReferenceMoney): GainsForMonth = {
      if (gain.isZero) {
        empty
      } else {
        GainsForMonth(Map(reservoir -> gain))
      }
    }

    def sum(gains: Seq[GainsForMonth]): GainsForMonth =
      GainsForMonth(
        reservoirToGains = combineMapValues(gains.map(_.reservoirToGains))(_.sum).filterNot(_._2.isZero)
      )
  }
}
