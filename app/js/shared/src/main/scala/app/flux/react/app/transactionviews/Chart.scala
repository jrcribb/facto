package app.flux.react.app.transactionviews

import scala.scalajs.js.JSConverters._
import java.time.Month
import app.common.money.Currency

import scala.collection.immutable.Seq
import app.common.money.CurrencyValueManager
import app.common.money.Money
import app.common.money.ReferenceMoney
import app.common.time.DatedMonth
import app.common.accounting.ChartSpec
import app.common.accounting.ChartSpec.AggregationPeriod
import app.common.accounting.ChartSpec.Line
import app.common.time.AccountingYear
import app.common.time.YearRange
import app.flux.router.AppPages
import app.flux.stores.entries.factories.ChartStoreFactory
import app.flux.stores.entries.factories.ChartStoreFactory.LinePoints
import app.flux.stores.InMemoryUserConfigStore
import app.models.access.AppJsEntityAccess
import app.models.accounting.config.Config
import app.models.user.User
import hydro.common.I18n
import hydro.common.JsLoggingUtils.logExceptions
import hydro.common.time.Clock
import hydro.flux.react.uielements.PageHeader
import hydro.flux.react.HydroReactComponent
import hydro.flux.react.uielements.Bootstrap
import hydro.flux.react.ReactVdomUtils.<<
import hydro.flux.react.uielements.Panel
import hydro.flux.react.ReactVdomUtils.^^
import hydro.flux.router.RouterContext
import hydro.jsfacades.Recharts
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

import scala.collection.mutable
import scala.scalajs.js

final class Chart(implicit
    summaryTable: SummaryTable,
    entityAccess: AppJsEntityAccess,
    user: User,
    clock: Clock,
    accountingConfig: Config,
    currencyValueManager: CurrencyValueManager,
    i18n: I18n,
    pageHeader: PageHeader,
    chartSpecInput: ChartSpecInput,
    chartStoreFactory: ChartStoreFactory,
) extends HydroReactComponent {

  private val lineColors: Seq[String] =
    Seq("purple", "orange", "green", "deeppink", "#DD0", "fuchsia", "red", "blue")

  // **************** API ****************//
  def apply(chartSpec: ChartSpec, router: RouterContext): VdomElement = {
    component(
      Props(
        chartSpec = chartSpec,
        router = router,
      )
    )
  }

  // **************** Implementation of HydroReactComponent methods ****************//
  override protected val config = ComponentConfig(
    backendConstructor = new Backend(_),
    initialState = State(),
    stateStoresDependencies = Some(props =>
      for (line <- props.chartSpec.lines) yield {
        val store =
          chartStoreFactory.get(line.query, correctForInflation = props.chartSpec.correctForInflation)
        StateStoresDependency(
          store,
          oldState => oldState.copy(lineToPoints = oldState.lineToPoints.updated(line, store.state)),
        )
      }
    ),
  )

  // **************** Private inner types ****************//
  protected case class Props(
      chartSpec: ChartSpec,
      router: RouterContext,
  )
  protected case class State(
      lineToPoints: Map[Line, LinePoints] = Map()
  )

  protected class Backend($ : BackendScope[Props, State]) extends BackendBase($) {

    override def render(props: Props, state: State) = logExceptions {
      implicit val router = props.router
      implicit val _ = props
      implicit val __ = state

      val chartData = assembleData()

      <.span(
        ^.className := "charts-page",
        pageHeader(router.currentPage),
        Bootstrap.Row(
          Bootstrap.Col(lg = 8)(
            // **************** Chartspec **************** //
            Bootstrap.Panel()(
              Bootstrap.PanelHeading(i18n("app.graph-lines")),
              Bootstrap.PanelBody(
                chartSpecInput(
                  chartSpec = props.chartSpec,
                  onChartSpecUpdate = newChartSpec => {
                    router.setPage(AppPages.Chart.fromChartSpec(newChartSpec))
                    Callback.empty
                  },
                )
              ),
            )
          ),
          <<.ifThen(accountingConfig.predefinedCharts.nonEmpty) {
            Bootstrap.Col(lg = 4)(
              // **************** Predefined charts **************** //
              Bootstrap.Panel()(
                Bootstrap.PanelHeading(i18n("app.predefined-charts")),
                Bootstrap.PanelBody(
                  <.ul(
                    accountingConfig.predefinedCharts.map { predefinedChart =>
                      <.li(
                        ^.key := predefinedChart.name,
                        if (predefinedChart.chartSpec == props.chartSpec)
                          <.b(predefinedChart.name)
                        else
                          router.anchorWithHrefTo(AppPages.Chart.fromChartSpec(predefinedChart.chartSpec))(
                            predefinedChart.name
                          ),
                      )
                    }.toVdomArray
                  )
                ),
              )
            )
          },
        ),
        // **************** Chart title **************** //
        <<.ifDefined(accountingConfig.predefinedCharts.find(_.chartSpec == props.chartSpec)) {
          predefinedChart =>
            <.h2(predefinedChart.name)
        },
        // **************** Chart **************** //
        <.div(
          Recharts.ResponsiveContainer(width = "100%", height = 450)(
            Recharts.ComposedChart(
              data = chartData,
              margin = Recharts.Margin(top = 5, right = 50, left = 50, bottom = 35),
            )(
              Recharts.CartesianGrid(strokeDasharray = "3 3", vertical = false),
              Recharts.XAxis(
                dataKey = "x",
                tickFormatter = s =>
                  props.chartSpec.aggregationPeriod match {
                    case AggregationPeriod.Month => s.toString.takeRight(4)
                    case AggregationPeriod.Year  => s.toString
                  },
                ticks = assembleTicks().toJSArray,
              ),
              Recharts.YAxis(tickFormatter = formatDoubleMoney(roundToInteger = true)),
              Recharts.Tooltip(formatter = formatDoubleMoney()),
              Recharts.Legend(),
              (for ((line, lineIndex) <- props.chartSpec.lines.zipWithIndex)
                yield {
                  if (line.showBars)
                    Recharts.Bar(
                      key = line.name,
                      dataKey = line.name,
                      fill = lineColors(lineIndex % lineColors.size),
                      stackId = "single-stack-id",
                    )
                  else
                    Recharts.Line(
                      tpe = "linear",
                      key = line.name,
                      dataKey = line.name,
                      stroke = lineColors(lineIndex % lineColors.size),
                    )
                }).toVdomArray,
            )
          ),
          <<.ifThen(props.chartSpec.lines.exists(_.cumulative)) {
            <.div(
              i18n("app.cumulated-total", formatMonth(DatedMonth.current)),
              ":",
              <.ul(
                (
                  for {
                    (line, lineIndex) <- props.chartSpec.lines.zipWithIndex
                    if line.cumulative
                  } yield {
                    <.li(
                      ^.key := lineIndex,
                      i18n("app.for-query", line.name),
                      ": ",
                      <.span(
                        ^^.ifThen(props.chartSpec.correctForInflation)(
                          ^.className := "corrected-for-inflation"
                        ),
                        chartData.lastOption
                          .map(data => data(line.name))
                          .map(formatDoubleMoney())
                          .getOrElse("0.00"): String,
                      ),
                    )
                  }
                ).toVdomArray
              ),
            )
          },
        ),
      )
    }

    private def assembleData()(implicit props: Props, state: State): Seq[Map[String, js.Any]] = {
      props.chartSpec.aggregationPeriod match {
        case AggregationPeriod.Month =>
          assembleDataGeneric[DatedMonth](
            keys = getAllMonths(),
            getFlowAtKey = (month, linePoints) =>
              linePoints.points
                .getOrElse(month, ReferenceMoney(0)),
            formatKey = formatMonth,
          )
        case AggregationPeriod.Year =>
          assembleDataGeneric[AccountingYear](
            keys = getAllYears(),
            getFlowAtKey = (year, linePoints) =>
              DatedMonth
                .allMonthsIn(year)
                .map(month =>
                  linePoints.points
                    .getOrElse(month, ReferenceMoney(0))
                )
                .sum,
            formatKey = _.toHumanReadableString(compact = true),
          )
      }
    }

    private def assembleDataGeneric[K](
        keys: Seq[K],
        getFlowAtKey: (K, LinePoints) => ReferenceMoney,
        formatKey: K => String,
    )(implicit props: Props, state: State): Seq[Map[String, js.Any]] = {
      val cumulativeMap = mutable.Map[Line, ReferenceMoney]().withDefaultValue(ReferenceMoney(0))

      for (key <- keys) yield {
        Map[String, js.Any](
          "x" -> formatKey(key)
        ) ++ props.chartSpec.lines.map { line =>
          val amount = getFlowAtKey(
            key,
            state
              .lineToPoints(line),
          )
          line.name -> {
            val result = {
              if (line.cumulative) {
                val newCumulativeAmount = cumulativeMap(line) + amount
                cumulativeMap.put(line, newCumulativeAmount)
                newCumulativeAmount
              } else {
                amount
              }
            }
            (if (line.inverted) -result.toDouble else result.toDouble): js.Any
          }
        }
      }
    }

    private def assembleTicks()(implicit props: Props, state: State): Seq[String] = {
      props.chartSpec.aggregationPeriod match {
        case AggregationPeriod.Month => getAllMonths().filter(_.month == Month.JANUARY).map(formatMonth)
        case AggregationPeriod.Year  => getAllYears().map(_.toHumanReadableString(compact = true))
      }
    }

    private def getAllMonths()(implicit props: Props, state: State): Seq[DatedMonth] = {
      val allDatesWithData = state.lineToPoints.flatMap(_._2.points.keySet)
      if (allDatesWithData.isEmpty) {
        Seq()
      } else {
        DatedMonth.monthsInClosedRange(allDatesWithData.min, allDatesWithData.max)
      }
    }

    private def getAllYears()(implicit props: Props, state: State): Seq[AccountingYear] = {
      val allDatesWithData = state.lineToPoints.flatMap(_._2.points.keySet)
      if (allDatesWithData.isEmpty) {
        Seq()
      } else {
        YearRange.closed(allDatesWithData.min.accountingYear, allDatesWithData.max.accountingYear).toSeq
      }
    }

    private def formatMonth(month: DatedMonth): String = {
      s"${month.abbreviation} ${month.year}"
    }

    private def formatDoubleMoney(roundToInteger: Boolean = false)(amount: Any): String = {
      val money = ReferenceMoney(Money.floatToCents(amount.asInstanceOf[Double]))
      val moneyString = if (roundToInteger) money.formatFloat.dropRight(3) else money.formatFloat
      val nonBreakingSpace = "\u00A0"
      s"${money.currency.symbol}${nonBreakingSpace}$moneyString"
    }
  }
}
