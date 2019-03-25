package app.flux.react.app.transactionviews

import hydro.common.I18n
import app.common.money.ExchangeRateManager
import app.models.access.AppJsEntityAccess
import app.models.accounting.config.Config
import app.models.user.User
import hydro.common.LoggingUtils.LogExceptionsCallback
import hydro.common.LoggingUtils.logExceptions
import hydro.common.time.Clock
import hydro.flux.react.uielements.PageHeader
import hydro.flux.react.uielements.input.TextInput
import hydro.flux.router.RouterContext
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

import scala.collection.immutable.Seq

final class Summary(implicit summaryTable: SummaryTable,
                    entityAccess: AppJsEntityAccess,
                    user: User,
                    clock: Clock,
                    accountingConfig: Config,
                    exchangeRateManager: ExchangeRateManager,
                    i18n: I18n,
                    pageHeader: PageHeader,
) {

  private val component = ScalaComponent
    .builder[Props](getClass.getSimpleName)
    .initialState(
      State(
        includeUnrelatedAccounts = false,
        query = "",
        yearLowerBound = clock.now.getYear - 1,
        expandedYear = clock.now.getYear))
    .renderBackend[Backend]
    .build

  // **************** API ****************//
  def apply(router: RouterContext): VdomElement = {
    component(Props(router))
  }

  // **************** Private inner types ****************//
  private case class Props(router: RouterContext)
  private case class State(includeUnrelatedAccounts: Boolean,
                           query: String,
                           yearLowerBound: Int,
                           expandedYear: Int)

  private class Backend(val $ : BackendScope[Props, State]) {
    val queryInputRef = TextInput.ref()

    def render(props: Props, state: State) = logExceptions {
      implicit val router = props.router
      <.span(
        pageHeader.withExtension(router.currentPage)(
          <.form(
            ^.className := "form-inline summary-query-filter",
            <.div(
              ^.className := "input-group",
              TextInput(
                ref = queryInputRef,
                name = "query",
                placeholder = i18n("app.example-query"),
                classes = Seq("form-control")),
              <.span(
                ^.className := "input-group-btn",
                <.button(
                  ^.className := "btn btn-default",
                  ^.tpe := "submit",
                  ^.onClick ==> { (e: ReactEventFromInput) =>
                    LogExceptionsCallback {
                      e.preventDefault()
                      $.modState(_.copy(query = queryInputRef().value getOrElse "")).runNow()
                    }
                  },
                  <.i(^.className := "fa fa-search")
                )
              )
            )
          )), {
          for {
            account <- accountingConfig.personallySortedAccounts
            if state.includeUnrelatedAccounts || account.isMineOrCommon
          } yield {
            summaryTable(
              key = account.code,
              account = account,
              query = state.query,
              yearLowerBound = state.yearLowerBound,
              expandedYear = state.expandedYear,
              onShowHiddenYears = $.modState(_.copy(yearLowerBound = Int.MinValue)),
              onSetExpandedYear = year => $.modState(_.copy(expandedYear = year))
            )
          }
        }.toVdomArray,
        // includeUnrelatedAccounts toggle button
        <.a(
          ^.className := "btn btn-info btn-lg btn-block",
          ^.onClick --> $.modState(s => s.copy(includeUnrelatedAccounts = !s.includeUnrelatedAccounts)),
          if (state.includeUnrelatedAccounts) i18n("app.hide-other-accounts")
          else i18n("app.show-other-accounts")
        )
      )
    }
  }
}