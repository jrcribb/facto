package models.accounting.config

import collection.immutable.Seq

import play.Play.application
import play.api.Logger

import com.google.common.base.Throwables
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.CustomClassLoaderConstructor
import org.yaml.snakeyaml.introspector.BeanAccess

import common.Require.requireNonNullFields
import models.User
import models.accounting.config.MoneyReservoir.NullMoneyReservoir

case class Config(accounts: Map[String, Account],
                  categories: Map[String, Category],
                  moneyReservoirs: Map[String, MoneyReservoir],
                  templates: Seq[Template],
                  constants: Constants) {
  requireNonNullFields(this)
}

object Config {

  private val loadedConfig: Config = {
    try {
      // get configLocation
      val configLocation = application.configuration.getString("facto.accounting.configYamlFilePath")

      // get data
      val stringData = scala.io.Source.fromFile(configLocation).mkString

      // parse data
      val constr = new CustomClassLoaderConstructor(getClass.getClassLoader)
      val yaml = new Yaml(constr)
      yaml.setBeanAccess(BeanAccess.FIELD)
      val configData = yaml.load(stringData).asInstanceOf[Parsable.Config]

      // convert to parsed config
      configData.parse
    } catch {
      case e: Throwable =>
        val stackTrace = Throwables.getStackTraceAsString(e)
        Logger.error(s"Error when parsing accounting-config.yml: $stackTrace")
        throw e
    }
  }

  /** Maps code to account */
  val accounts: Map[String, Account] = loadedConfig.accounts
  /** Maps code to category */
  val categories: Map[String, Category] = loadedConfig.categories

  // Not exposing moneyReservoirs because it's too easy to accidentally show hidden reservoirs
  def moneyReservoir(code: String): MoneyReservoir = moneyReservoirOption(code).get
  def moneyReservoirOption(code: String): Option[MoneyReservoir] = code match {
    case "" => Some(NullMoneyReservoir)
    case _ => loadedConfig.moneyReservoirs.get(code)
  }

  def moneyReservoirs(includeNullReservoir: Boolean = false, includeHidden: Boolean = false): Seq[MoneyReservoir] = {
    var result = loadedConfig.moneyReservoirs.values.toVector
    if (!includeHidden) {
      result = result.filter(!_.hidden)
    }
    if (includeNullReservoir) {
      result ++= Seq(NullMoneyReservoir)
    }
    result
  }

  // Shortcuts for moneyReservoirs(), added because visibleReservoirs makes it clearer that only visible reservoirs
  // are returned than the equivalent moneyReservoirs()
  val visibleReservoirs: Seq[MoneyReservoir] = moneyReservoirs()
  def visibleReservoirs(includeNullReservoir: Boolean = false): Seq[MoneyReservoir] =
    moneyReservoirs(includeNullReservoir = includeNullReservoir)

  def templatesToShowFor(location: Template.Placement, user: User): Seq[Template] =
    loadedConfig.templates filter (_.showFor(location, user))

  def templateWithCode(code: String): Template = {
    val codeToTemplate = {
      for (tpl <- loadedConfig.templates) yield tpl.code -> tpl
    }.toMap
    codeToTemplate(code)
  }

  val constants: Constants = loadedConfig.constants

  def accountOf(user: User): Option[Account] = accounts.values.filter(_.user == Some(user)).headOption

  def personallySortedAccounts(implicit user: User): Seq[Account] = {
    val myAccount = accountOf(user)
    val otherAccounts = for {
      acc <- accounts.values
      if !Set(Some(constants.commonAccount), myAccount).flatten.contains(acc)
    } yield acc
    Seq(List(constants.commonAccount), myAccount.toList, otherAccounts).flatten
  }
}
