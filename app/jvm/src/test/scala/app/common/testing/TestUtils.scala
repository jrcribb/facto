package app.common.testing

import java.time.Instant
import java.time.ZoneId

import app.common.testing.TestObjects._
import hydro.common.time.LocalDateTime
import hydro.models.Entity
import app.models.access.JvmEntityAccess
import app.models.accounting.config.Account
import app.models.accounting.config.Category
import app.models.accounting.config.MoneyReservoir
import app.models.accounting.BalanceCheck
import app.models.accounting.Transaction
import app.models.accounting.TransactionGroup
import app.models.modification.EntityModification
import app.models.modification.EntityType
import app.models.money.ExchangeRateMeasurement
import app.models.accounting.TransactionGroup
import app.models.accounting.Transaction
import app.models.accounting.BalanceCheck
import app.models.user.User
import app.models.user.User

import scala.collection.immutable.Seq

object TestUtils {

  def persistTransaction(groupId: Long = -1,
                         flowInCents: Long = 0,
                         date: LocalDateTime = FakeClock.defaultLocalDateTime,
                         timestamp: Long = -1,
                         account: Account = testAccount,
                         category: Category = testCategory,
                         reservoir: MoneyReservoir = testReservoir,
                         description: String = "description",
                         detailDescription: String = "detailDescription",
                         tags: Seq[String] = Seq())(implicit entityAccess: JvmEntityAccess): Transaction = {
    val actualGroupId = if (groupId == -1) {
      persist(TransactionGroup(createdDate = FakeClock.defaultLocalDateTime)).id
    } else {
      groupId
    }
    val actualDate = if (timestamp == -1) date else localDateTimeOfEpochSecond(timestamp)
    persist(
      Transaction(
        transactionGroupId = actualGroupId,
        issuerId = 1,
        beneficiaryAccountCode = account.code,
        moneyReservoirCode = reservoir.code,
        categoryCode = category.code,
        description = description,
        detailDescription = detailDescription,
        flowInCents = flowInCents,
        tags = tags,
        createdDate = actualDate,
        transactionDate = actualDate,
        consumedDate = actualDate
      ))
  }

  def persistBalanceCheck(
      balanceInCents: Long = 0,
      date: LocalDateTime = FakeClock.defaultLocalDateTime,
      timestamp: Long = -1,
      reservoir: MoneyReservoir = testReservoir)(implicit entityAccess: JvmEntityAccess): BalanceCheck = {
    val actualDate = if (timestamp == -1) date else localDateTimeOfEpochSecond(timestamp)
    persist(
      BalanceCheck(
        issuerId = 2,
        moneyReservoirCode = reservoir.code,
        balanceInCents = balanceInCents,
        createdDate = actualDate,
        checkDate = actualDate
      ))
  }

  def persist[E <: Entity: EntityType](entity: E)(implicit entityAccess: JvmEntityAccess): E = {
    implicit val user = User(
      idOption = Some(9213982174887321L),
      loginName = "robot",
      passwordHash = "Some hash",
      name = "Robot",
      isAdmin = false,
      expandCashFlowTablesByDefault = true,
      expandLiquidationTablesByDefault = true
    )
    val addition =
      if (entity.idOption.isDefined) EntityModification.Add(entity)
      else EntityModification.createAddWithRandomId(entity)
    entityAccess.persistEntityModifications(addition)
    addition.entity
  }

  def localDateTimeOfEpochSecond(milli: Long): LocalDateTime = {
    val instant = Instant.ofEpochSecond(milli).atZone(ZoneId.of("Europe/Paris"))
    LocalDateTime.of(
      instant.toLocalDate,
      instant.toLocalTime
    )
  }
}
