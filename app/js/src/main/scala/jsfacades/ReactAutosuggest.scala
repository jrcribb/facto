package jsfacades

import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.{Children, JsComponent}
import org.scalajs.dom.ext.KeyCode

import scala.collection.immutable.Seq
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala2js.Converters._

object ReactAutosuggest {

  // **************** API ****************//
  def apply(suggestions: Seq[String],
            onSuggestionsFetchRequested: String => Unit,
            onSuggestionsClearRequested: () => Unit,
            renderSuggestion: String => js.Any,
            inputProps: InputProps,
            theme: Theme) = {
    val component = JsComponent[js.Object, Children.None, Null](js.Dynamic.global.Autosuggest)
    component(
      Props(
        suggestions = suggestions.toJSArray,
        onSuggestionsFetchRequested = params =>
          onSuggestionsFetchRequested(params.value.asInstanceOf[String]),
        onSuggestionsClearRequested = onSuggestionsClearRequested,
        getSuggestionValue = s => s,
        renderSuggestion = renderSuggestion,
        inputProps = js.Dynamic.literal(
          value = inputProps.value,
          onChange = (event: js.Any, params: js.Dynamic) =>
            inputProps.onChange(params.newValue.asInstanceOf[String])),
        theme = js.Dynamic.literal(
          container = theme.container,
          input = theme.input,
          suggestionsContainer = theme.suggestionsContainer,
          suggestionsList = theme.suggestionsList,
          suggestion = theme.suggestion,
          suggestionHighlighted = theme.suggestionHighlighted
        )
      ).toJsObject)
  }

  // **************** Public inner types ****************//
  case class InputProps(value: String, onChange: String => Unit)

  case class Theme(container: String,
                   input: String,
                   suggestionsContainer: String,
                   suggestionsList: String,
                   suggestion: String,
                   suggestionHighlighted: String)

  // **************** Private inner types ****************//
  private case class Props(suggestions: js.Array[String],
                           onSuggestionsFetchRequested: js.Function1[js.Dynamic, Unit],
                           onSuggestionsClearRequested: js.Function0[Unit],
                           getSuggestionValue: js.Function1[String, String],
                           renderSuggestion: js.Function1[String, js.Any],
                           inputProps: js.Object,
                           theme: js.Object) {
    def toJsObject: js.Object =
      js.Dynamic.literal(
        suggestions = suggestions,
        onSuggestionsFetchRequested = onSuggestionsFetchRequested,
        onSuggestionsClearRequested = onSuggestionsClearRequested,
        getSuggestionValue = getSuggestionValue,
        renderSuggestion = renderSuggestion,
        inputProps = inputProps,
        theme = theme
      )
  }
}
