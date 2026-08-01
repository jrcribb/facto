package app.flux.react.app.transactiongroupform

import hydro.flux.react.uielements.Bootstrap
import hydro.flux.react.uielements.Bootstrap.Size
import hydro.flux.react.uielements.Bootstrap.Variant
import hydro.common.I18n
import app.flux.react.app.transactiongroupform.TotalFlowRestrictionInput.TotalFlowRestriction
import hydro.common.JsLoggingUtils.LogExceptionsCallback
import hydro.common.JsLoggingUtils.logExceptions
import hydro.flux.react.ReactVdomUtils.^^
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

import scala.collection.immutable.Seq

private[transactiongroupform] final class TotalFlowRestrictionInput(implicit i18n: I18n) {

  private val component = ScalaComponent
    .builder[Props](getClass.getSimpleName)
    .stateless
    .render_P(props =>
      logExceptions {
        def button(totalFlowRestriction: TotalFlowRestriction, label: String, disabled: Boolean = false) = {
          Bootstrap.Button(size = Size.sm, tag = <.label)(
            ^^.ifThen(props.value == totalFlowRestriction) {
              ^.className := "active"
            },
            ^^.ifThen(disabled) {
              ^.disabled := true
            },
            ^.onClick --> LogExceptionsCallback {
              if (!disabled) {
                props.onChangeListener(totalFlowRestriction)
              }
            },
            label,
          )
        }

        Bootstrap.ButtonGroup(
          VdomAttr("data-toggle") := "buttons",
          button(TotalFlowRestriction.AnyTotal, i18n("app.any-total")),
          button(TotalFlowRestriction.ChooseTotal, i18n("app.choose-total")),
          button(TotalFlowRestriction.ZeroSum, i18n("app.zero-sum"), disabled = !props.allowZeroSum),
        )
      }
    )
    .build

  // **************** API ****************//
  def apply(value: TotalFlowRestriction, allowZeroSum: Boolean, onChange: TotalFlowRestriction => Unit): VdomElement = {
    component(Props(value, allowZeroSum, onChange))
  }

  // **************** Private inner types ****************//
  private case class Props(value: TotalFlowRestriction, allowZeroSum: Boolean, onChangeListener: TotalFlowRestriction => Unit)
}

object TotalFlowRestrictionInput {
  // **************** Public inner types ****************//
  sealed trait TotalFlowRestriction {
    def userSetsTotal: Boolean
  }
  object TotalFlowRestriction {
    object AnyTotal extends TotalFlowRestriction {
      override def userSetsTotal = false
    }
    object ChooseTotal extends TotalFlowRestriction {
      override def userSetsTotal = true
    }
    object ZeroSum extends TotalFlowRestriction {
      override def userSetsTotal = true
    }
  }
}
