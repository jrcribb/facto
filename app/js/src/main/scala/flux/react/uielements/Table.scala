package flux.react.uielements

import scala.scalajs.js
import common.I18n
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom._
import japgolly.scalajs.react.vdom.html_<^._
import flux.react.ReactVdomUtils.{^^, <<}

import scala.collection.immutable.Seq

object Table {

  private val component = ScalaComponent
    .builder[Props](getClass.getSimpleName)
    .renderP((_, props) =>
      <.table(
        ^^.classes(
          Seq("table", "table-bordered", "table-hover", "table-condensed", "table-overflow-elipsis") ++ props.tableClasses),
        <.thead(
          <.tr(^^.classes("info"), <.th(^.colSpan := props.colSpan, props.title)),
          <.tr(props.tableHeaders)
        ),
        <.tbody(
          props.tableDatas.zipWithIndex
            .map {
              case (tableData, index) => <.tr(^.key := s"row-$index", ^.className := "data-row", tableData)
            },
          if (props.tableDatas.isEmpty) {
            <.tr(
              <.td(^.colSpan := props.colSpan, ^^.classes("no-entries"), props.i18n("facto.no-entries"))
            )
          } else if (props.expandNumEntriesCallback.isDefined) {
            <.tr(
              <.td(
                ^.colSpan := props.colSpan,
                ^.style := js.Dictionary("textAlign" -> "center"),
                <.a(
                  ^.onClick --> props.expandNumEntriesCallback.get,
                  ^.tpe := "button",
                  ^^.classes("btn", "btn-sm", "btn-default", "btn-circle", "expand-num-entries"),
                  <.i(^^.classes("fa", "fa-ellipsis-h"))
                )
              )
            )
          } else {
            Seq()
          }
        )
    ))
    .build

  def apply(title: String,
            tableClasses: Seq[String] = Seq(),
            expandNumEntriesCallback: Option[Callback] = None,
            tableHeaders: Seq[VdomElement],
            tableDatas: Seq[Seq[VdomElement]])(implicit i18n: I18n): VdomElement = {
    component(Props(title, tableClasses, expandNumEntriesCallback, tableHeaders, tableDatas, i18n))
  }

  private case class Props(title: String,
                           tableClasses: Seq[String],
                           expandNumEntriesCallback: Option[Callback],
                           tableHeaders: Seq[VdomElement],
                           tableDatas: Seq[Seq[VdomElement]],
                           i18n: I18n) {
    def colSpan: Int = tableHeaders.size
  }
}
