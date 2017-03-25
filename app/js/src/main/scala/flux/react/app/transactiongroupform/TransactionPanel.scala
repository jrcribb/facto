package flux.react.app.transactiongroupform

import common.{I18n, LoggingUtils}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import flux.react.ReactVdomUtils.^^
import flux.react.uielements
import org.scalajs.dom.raw.HTMLInputElement

import scala.collection.immutable.Seq

private[transactiongroupform] object TransactionPanel {

  private val price1Ref = uielements.bootstrap.TextInput.ref("price1")
  private val price2Ref = InputWithDefaultFromReference.ref("price2")
  private val component = ReactComponentB[Props](getClass.getSimpleName)
    .renderBackend[Backend]
    .build

  // **************** API ****************//
  def apply(key: Int, closeButtonCallback: Option[Callback] = None)(implicit i18n: I18n): ReactElement = {
    component.withKey(key)(Props("testTitle", closeButtonCallback, i18n))
  }

  // **************** Private inner types ****************//
  private type State = Unit

  private case class Props(title: String,
                           deleteButtonCallback: Option[Callback],
                           i18n: I18n)

  private class Backend($: BackendScope[Props, State]) {

    def render(props: Props, state: State) = LoggingUtils.logExceptions {
      HalfPanel(
        title = <.span(props.title),
        closeButtonCallback = props.deleteButtonCallback)(
        uielements.bootstrap.TextInput("label", "price 1", ref = price1Ref),
        InputWithDefaultFromReference(
          ref = price2Ref,
          defaultValueProxy = price1Ref($))(extraProps =>
          uielements.bootstrap.TextInput(
            "label", "price 2", inputClasses = extraProps.inputClasses, ref = extraProps.ref)
        ),
        <.button(
          ^.onClick --> Callback {
            println("  Price 1:" + price1Ref($).value)
            println("  Price 2:" + price2Ref($).input.value)
          },
          "Test button"
        )
      )
    }
  }
}
