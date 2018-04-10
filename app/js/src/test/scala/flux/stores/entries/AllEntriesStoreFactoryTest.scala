package flux.stores.entries

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.async.Async.{async, await}
import java.time.Month.JANUARY

import common.testing.FakeJsEntityAccess
import common.testing.TestObjects._
import common.time.LocalDateTime
import common.time.LocalDateTimes.createDateTime
import models.accounting._
import models.modification.EntityModification
import utest._

import scala.collection.immutable.Seq
import scala2js.Converters._

/** Test test also tests `EntriesStoreFactory` and `EntriesStore`. */
object AllEntriesStoreFactoryTest extends TestSuite {

  override def tests = TestSuite {
    implicit val entityAccess: FakeJsEntityAccess = new FakeJsEntityAccess()
    val factory: AllEntriesStoreFactory = new AllEntriesStoreFactory()
    val store: factory.Store = factory.get(maxNumEntries = 3)

    "factory result is cached" - async {
      factory.get(maxNumEntries = 3) ==> store
      assert(factory.get(maxNumEntries = 4) != store)
    }

    "store state is updated upon remote update" - async {
      await(store.stateFuture) ==> EntriesListStoreFactory.State.empty

      entityAccess.addRemotelyAddedEntities(testTransactionWithId)

      await(store.stateFuture).hasMore ==> false
      await(store.stateFuture).entries ==> GeneralEntry.toGeneralEntrySeq(Seq(testTransactionWithId))
      store.state.get.entries ==> GeneralEntry.toGeneralEntrySeq(Seq(testTransactionWithId))
    }

    "store state is updated upon local update" - async {
      await(store.stateFuture) ==> EntriesListStoreFactory.State.empty

      entityAccess.persistModifications(Seq(EntityModification.Add(testTransactionWithId)))

      await(store.stateFuture).hasMore ==> false
      await(store.stateFuture).entries ==> GeneralEntry.toGeneralEntrySeq(Seq(testTransactionWithId))
    }

    "store state is updated upon local removal" - async {
      entityAccess.persistModifications(Seq(EntityModification.Add(testTransactionWithId)))
      await(store.stateFuture).hasMore ==> false
      await(store.stateFuture).entries ==> GeneralEntry.toGeneralEntrySeq(Seq(testTransactionWithId))

      entityAccess.persistModifications(Seq(EntityModification.Remove[Transaction](testTransactionWithId.id)))

      await(store.stateFuture) ==> EntriesListStoreFactory.State.empty
    }

    "store calls listeners" - async {
      var onStateUpdateCount = 0
      store.register(() => {
        onStateUpdateCount += 1
      })

      await(store.stateFuture)

      onStateUpdateCount ==> 1

      entityAccess.persistModifications(Seq(EntityModification.Add(testTransactionWithIdB)))
      await(store.stateFuture)

      onStateUpdateCount ==> 2

      entityAccess.addRemotelyAddedEntities(testTransactionWithIdA)
      await(store.stateFuture)

      onStateUpdateCount ==> 3
    }

    "combines consecutive transactions" - async {
      val trans1 = testTransactionWithIdA.copy(idOption = Some(91), transactionGroupId = 122)
      val trans2 = testTransactionWithIdA.copy(idOption = Some(501), transactionGroupId = 122)
      val trans3 = testTransactionWithIdA.copy(idOption = Some(1234567890), transactionGroupId = 133)

      entityAccess.addRemotelyAddedEntities(trans1, trans2, trans3)

      await(store.stateFuture).hasMore ==> false
      await(store.stateFuture).entries ==> GeneralEntry.toGeneralEntrySeq(Seq(trans1, trans2), Seq(trans3))
    }

    "sorts entries on transaction date first and then created date" - async {
      def transaction(id: Long, transactionDate: LocalDateTime, createdDate: LocalDateTime): Transaction = {
        testTransactionWithIdA.copy(
          idOption = Some(id),
          transactionGroupId = id,
          createdDate = createdDate,
          transactionDate = transactionDate)
      }
      val trans1 = transaction(
        111,
        transactionDate = createDateTime(2012, JANUARY, 2),
        createdDate = createDateTime(2012, JANUARY, 3))
      val trans2 = transaction(
        222,
        transactionDate = createDateTime(2012, JANUARY, 3),
        createdDate = createDateTime(2012, JANUARY, 1))
      val trans3 = transaction(
        333,
        transactionDate = createDateTime(2012, JANUARY, 3),
        createdDate = createDateTime(2012, JANUARY, 2))

      entityAccess.addRemotelyAddedEntities(trans3, trans2, trans1)

      await(store.stateFuture).hasMore ==> false
      await(store.stateFuture).entries ==> GeneralEntry
        .toGeneralEntrySeq(Seq(trans1), Seq(trans2), Seq(trans3))
    }

    "respects maxNumEntries" - async {
      def transaction(id: Long, date: LocalDateTime): Transaction = {
        testTransactionWithIdA.copy(idOption = Some(id), transactionGroupId = id, createdDate = date)
      }
      val trans1 = transaction(111, createDateTime(2012, JANUARY, 1))
      val trans2 = transaction(222, createDateTime(2012, JANUARY, 2))
      val trans3 = transaction(333, createDateTime(2012, JANUARY, 3))
      val trans4 = transaction(444, createDateTime(2012, JANUARY, 4))

      entityAccess.addRemotelyAddedEntities(trans1, trans2, trans3, trans4)

      await(store.stateFuture).hasMore ==> true
      await(store.stateFuture).entries ==> GeneralEntry
        .toGeneralEntrySeq(Seq(trans2), Seq(trans3), Seq(trans4))
    }
  }
}