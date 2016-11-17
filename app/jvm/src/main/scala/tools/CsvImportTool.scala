package tools

import scala.io.Source
import scala.collection.JavaConverters._
import java.nio.file.Path

import com.google.inject.Inject
import play.api.Logger
import com.google.common.base.Splitter
import org.joda.time.DateTime
import common.ResourceFiles
import models.SlickUtils.dbApi._
import models.SlickUtils.dbRun
import models._
import models.accounting.money.Money
import models.accounting.{BalanceCheck, BalanceChecks, Transaction, TransactionGroup, TransactionGroups, Transactions, UpdateLogs}

final class CsvImportTool @Inject()(implicit userManager: User.Manager,
                                    entityAccess: SlickEntityAccess) {

  def importTransactions(csvFilePath: Path): Unit = {
    // example of line: "2 :: Common :: LIFE :: CARD_COMMON :: imperdiet Duis  :: -25.04 :: 1425855600 :: 0 :: 1425934823"
    val lines = for (line <- ResourceFiles.readLines(csvFilePath) if (!line.trim.isEmpty)) yield line.trim
    for (line <- lines) {
      val parts = Splitter.on(" :: ").trimResults().split(line).asScala.toList
      parts match {
        case List(issuerId, beneficiaryAccountCode, categoryCode, moneyReservoirCode, description, flowAsFloat,
        transactionDateStamp, consumedDateStamp, createdDateStamp) =>
          val group = TransactionGroups.add(TransactionGroup())
          Transactions.add(Transaction(
            transactionGroupId = group.id,
            issuerId = issuerId.toInt,
            beneficiaryAccountCode = beneficiaryAccountCode,
            moneyReservoirCode = moneyReservoirCode,
            categoryCode = categoryCode,
            description = description,
            flowInCents = Money.floatToCents(flowAsFloat.toDouble),
            tagsString = s"csv-import-$beneficiaryAccountCode",
            createdDate = new DateTime(createdDateStamp.toLong * 1000),
            transactionDate = new DateTime(transactionDateStamp.toLong * 1000),
            consumedDate = new DateTime(if (consumedDateStamp.toLong == 0) transactionDateStamp.toLong * 1000 else consumedDateStamp.toLong * 1000)
          ))
      }
    }
  }

  def importBalanceChecks(csvFilePath: Path): Unit = {
    // example of line: "2 :: CASH_COMMON :: 40.58 :: 1426287600 :: 1426357095"
    val lines = for (line <- ResourceFiles.readLines(csvFilePath) if (!line.trim.isEmpty)) yield line.trim
    for (line <- lines) {
      val parts = Splitter.on(" :: ").trimResults().split(line).asScala.toList
      parts match {
        case List(issuerId, moneyReservoirCode, balanceAsFloat, checkDateStamp, createdDateStamp) =>
          val group = TransactionGroups.add(TransactionGroup())
          BalanceChecks.add(BalanceCheck(
            issuerId = issuerId.toInt,
            moneyReservoirCode = moneyReservoirCode,
            balanceInCents = Money.floatToCents(balanceAsFloat.toDouble),
            createdDate = new DateTime(createdDateStamp.toLong * 1000),
            checkDate = new DateTime(checkDateStamp.toLong * 1000)
          ))
      }
    }
  }
}
