package flux.react

import common.LoggingUtils.logExceptions
import flux.FactoAppModule
import org.scalajs.dom
import org.scalajs.dom.console
import org.scalajs.dom.raw.{ErrorEvent, Event}

import scala.async.Async.{async, await}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("FactoApp")
object FactoApp extends js.JSApp {

  @JSExport
  override def main(): Unit = async {
    console.log("  Application starting")
    // send log messages also to the server
    //log.enableServerLogging("/logging")
    //log.info("This message goes to server as well")

    // create stylesheet
    //GlobalStyles.addToDocument()

    // Log all uncaught errors
    dom.window.onerror = (event, url, lineNumber, _) => console.log("  Uncaught error:", event)
    dom.window.addEventListener("error", (event: Event) => {
      console.log("  Uncaught error:", event)
      false
    })

    val commonTimeModule = new common.time.Module
    implicit val clock = commonTimeModule.clock

    val apiModule = new api.Module
    implicit val scalaJsApiClient = apiModule.scalaJsApiClient
    implicit val initialDataResponse = await(scalaJsApiClient.getInitialData())

    implicit val globalModule = new FactoAppModule()

    // tell React to render the router in the document body
    logExceptions {
      globalModule.router().renderIntoDOM(dom.document.getElementById("root"))
    }
  }
}