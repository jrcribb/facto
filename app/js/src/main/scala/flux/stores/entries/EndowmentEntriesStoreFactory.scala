package flux.stores.entries

import jsfacades.Loki
import models.access.RemoteDatabaseProxy
import models.accounting.Transaction
import models.accounting.config.{Account, Config}
import models.manager.{EntityModification, EntityType}

import scala.collection.immutable.Seq

final class EndowmentEntriesStoreFactory(implicit database: RemoteDatabaseProxy, accountingConfig: Config)
    extends EntriesListStoreFactory[GeneralEntry, Account] {

  override protected def createNew(maxNumEntries: Int, account: Account) = new Store {
    override protected def calculateState() = {
      val transactions: Seq[Transaction] =
        database
          .newQuery[Transaction]()
          .find("categoryCode" -> accountingConfig.constants.endowmentCategory.code)
          .find("beneficiaryAccountCode" -> account.code)
          .sort(
            Loki.Sorting
              .descBy("consumedDate")
              .thenDescBy("createdDate")
              .thenDescBy("id"))
          .limit(3 * maxNumEntries)
          .data()
          .reverse

      var entries = transactions.map(t => GeneralEntry(Seq(t)))

      entries = GeneralEntry.combineConsecutiveOfSameGroup(entries)

      EntriesListStoreFactory.State(entries.takeRight(maxNumEntries), hasMore = entries.size > maxNumEntries)
    }

    override protected def transactionUpsertImpactsState(transaction: Transaction, state: State): Boolean = {
      transaction.category == accountingConfig.constants.endowmentCategory && transaction.beneficiary == account
    }
  }

  def get(account: Account, maxNumEntries: Int): Store =
    get(Input(maxNumEntries = maxNumEntries, additionalInput = account))
}
