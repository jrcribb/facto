package app.flux.react.app

import scala.collection.immutable.Seq
import hydro.flux.react.uielements.SbadminLayout
import hydro.flux.router.RouterContext
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

final class Layout(implicit
    menu: Menu,
    sbadminLayout: SbadminLayout,
    inflationToggleButton: InflationToggleButton,
    keyboardShortcutsHelpOverlay: hydro.flux.react.uielements.KeyboardShortcutsHelpOverlay,
) {

  private val component = ScalaComponent
    .builder[Props](getClass.getSimpleName)
    .renderPC { (_, props, children) =>
      implicit val router = props.router
      sbadminLayout(
        title = "Family Accounting Tool",
        leftMenu = menu(),
        pageContent = <.span(children),
        extraNavbarTopRightContent = Seq(inflationToggleButton()),
        extraFooter = Seq(
          keyboardShortcutsHelpOverlay(
            Seq(
              "Navigation" -> Seq(
                "Shift + Alt + E / A" -> "Everything",
                "Shift + Alt + C" -> "Cash flow",
                "Shift + Alt + L / V" -> "Liquidation",
                "Shift + Alt + D" -> "Endowments",
                "Shift + Alt + S" -> "Summary",
                "Shift + Alt + R" -> "Chart",
                "Shift + Alt + T / J" -> "Templates",
                "Shift + Alt + N" -> "New entry",
                "Shift + Alt + Up / Down" -> "Previous / next menu item",
                "Shift + Alt + F" -> "Search",
              ),
              "General" -> Seq(
                "Shift + Alt + /" -> "Show this help",
              ),
            )
          )
        ),
      )
    }
    .build

  // **************** API ****************//
  def apply(router: RouterContext)(children: VdomNode*): VdomElement = {
    component(Props(router))(children: _*)
  }

  // **************** Private inner types ****************//
  private case class Props(router: RouterContext)
}
