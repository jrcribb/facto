package flux.stores.entries

import common.money.{ExchangeRateManager, MoneyWithGeneralCurrency}
import common.time.JavaTimeImplicits._
import common.time.LocalDateTime
import flux.stores.entries.CashFlowEntry.{BalanceCorrection, RegularEntry}
import jsfacades.LokiJs
import models.access.DbQuery
import models.access.DbQueryImplicits._
import models.EntityAccess
import models.access.RemoteDatabaseProxy
import models.accounting.config.{Config, MoneyReservoir}
import models.accounting.{Transaction, _}

import scala2js.Converters._
import models.access.Fields

final class CashFlowEntriesStoreFactory(implicit database: RemoteDatabaseProxy,
                                        accountingConfig: Config,
                                        exchangeRateManager: ExchangeRateManager,
                                        entityAccess: EntityAccess)
    extends EntriesListStoreFactory[CashFlowEntry, MoneyReservoir] {

  override protected def createNew(maxNumEntries: Int, moneyReservoir: MoneyReservoir) = new Store {
    override protected def calculateState() = {
      val oldestRelevantBalanceCheck: Option[BalanceCheck] = {
        val numTransactionsToFetch = 3 * maxNumEntries
        val totalNumTransactions =
          database
            .newQuery[Transaction]()
            .filter(Fields.Transaction.moneyReservoirCode isEqualTo moneyReservoir.code)
            .count()

        if (totalNumTransactions < numTransactionsToFetch) {
          None // get all entries

        } else {
          // get oldest oldestTransDate
          val oldestTransDate =
            database
              .newQuery[Transaction]()
              .filter(Fields.Transaction.moneyReservoirCode isEqualTo moneyReservoir.code)
              .sort(
                DbQuery.Sorting
                  .descBy(Fields.Transaction.transactionDate)
                  .thenDescBy(Fields.Transaction.createdDate)
                  .thenDescBy(Fields.id))
              .limit(numTransactionsToFetch)
              .data()
              .last
              .transactionDate

          // get relevant balance checks
          database
            .newQuery[BalanceCheck]()
            .filter(Fields.BalanceCheck.moneyReservoirCode isEqualTo moneyReservoir.code)
            .filter(Fields.BalanceCheck.checkDate < oldestTransDate)
            .sort(
              DbQuery.Sorting
                .descBy(Fields.BalanceCheck.checkDate)
                .thenDescBy(Fields.BalanceCheck.createdDate)
                .thenDescBy(Fields.id))
            .limit(1)
            .data()
            .headOption
        }
      }

      val oldestBalanceDate = oldestRelevantBalanceCheck.map(_.checkDate).getOrElse(LocalDateTime.MIN)
      val initialBalance =
        oldestRelevantBalanceCheck
          .map(_.balance)
          .getOrElse(MoneyWithGeneralCurrency(0, moneyReservoir.currency))

      val balanceChecks: Seq[BalanceCheck] =
        database
          .newQuery[BalanceCheck]()
          .filter(Fields.BalanceCheck.moneyReservoirCode isEqualTo moneyReservoir.code)
          .filter(Fields.BalanceCheck.checkDate >= oldestBalanceDate)
          .data()

      // get relevant transactions
      val transactions: Seq[Transaction] =
        database
          .newQuery[Transaction]()
          .filter(Fields.Transaction.moneyReservoirCode isEqualTo moneyReservoir.code)
          .filter(Fields.Transaction.transactionDate >= oldestBalanceDate)
          .data()

      // merge the two
      val mergedRows = (transactions ++ balanceChecks).sortBy {
        case trans: Transaction => (trans.transactionDate, trans.createdDate)
        case bc: BalanceCheck => (bc.checkDate, bc.createdDate)
      }

      // convert to entries (recursion does not lead to growing stack because of Stream)
      def convertToEntries(nextRows: List[AnyRef],
                           currentBalance: MoneyWithGeneralCurrency): Stream[CashFlowEntry] =
        (nextRows: @unchecked) match {
          case (trans: Transaction) :: rest =>
            val newBalance = currentBalance + trans.flow
            RegularEntry(List(trans), newBalance, balanceVerified = false) #:: convertToEntries(
              rest,
              newBalance)
          case (bc: BalanceCheck) :: rest =>
            BalanceCorrection(bc, expectedAmount = currentBalance) #:: convertToEntries(rest, bc.balance)
          case Nil =>
            Stream.empty
        }
      var entries = convertToEntries(mergedRows.toList, initialBalance).toList

      // combine entries of same group and merge BC's with same balance (recursion does not lead to growing stack because of Stream)
      def combineSimilar(nextEntries: List[CashFlowEntry]): Stream[CashFlowEntry] = nextEntries match {
        case (x: RegularEntry) :: (y: RegularEntry) :: rest if x.groupId == y.groupId =>
          combineSimilar(
            RegularEntry(x.transactions ++ y.transactions, y.balance, balanceVerified = false) :: rest)
        case (x: BalanceCorrection) :: (y: BalanceCorrection) :: rest
            if x.balanceCheck.balance == y.balanceCheck.balance =>
          combineSimilar(x :: rest)
        case entry :: rest =>
          entry #:: combineSimilar(rest)
        case Nil =>
          Stream.empty
      }
      entries = combineSimilar(entries).toList

      // merge validating BalanceCorrections into RegularEntries (recursion does not lead to growing stack because of Stream)
      def mergeValidatingBCs(nextEntries: List[CashFlowEntry]): Stream[CashFlowEntry] = nextEntries match {
        case (regular: RegularEntry) :: BalanceCorrection(bc, _) :: rest if regular.balance == bc.balance =>
          mergeValidatingBCs(regular.copy(balanceVerified = true) :: rest)
        case entry :: rest =>
          entry #:: mergeValidatingBCs(rest)
        case Nil =>
          Stream.empty
      }
      entries = mergeValidatingBCs(entries).toList

      EntriesListStoreFactory.State(
        entries.takeRight(maxNumEntries),
        hasMore = entries.size > maxNumEntries,
        impactingTransactionIds = transactions.toStream.map(_.id).toSet,
        impactingBalanceCheckIds = (balanceChecks.toStream ++ oldestRelevantBalanceCheck).map(_.id).toSet
      )
    }

    override protected def transactionUpsertImpactsState(transaction: Transaction, state: State) =
      transaction.moneyReservoir == moneyReservoir

    override protected def balanceCheckUpsertImpactsState(balanceCheck: BalanceCheck, state: State) =
      balanceCheck.moneyReservoir == moneyReservoir
  }

  def get(moneyReservoir: MoneyReservoir, maxNumEntries: Int): Store =
    get(Input(maxNumEntries = maxNumEntries, additionalInput = moneyReservoir))
}
