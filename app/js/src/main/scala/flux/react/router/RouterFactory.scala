package flux.react.router

import flux.react.router.RouterFactory.Page
import flux.react.router.RouterFactory.Page._
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.TagMod
import japgolly.scalajs.react.vdom.prefix_<^._

import scala.collection.immutable.Seq

trait RouterFactory {

  def createRouter(): Router[Page]
}

object RouterFactory {
  sealed trait Page
  object Page {
    case object EverythingPage extends Page
    case object NewTransactionGroupPage extends Page
    case object EverythingPage2 extends Page
    case object TestPage extends Page
  }

  private[router] final class Impl(implicit reactAppModule: flux.react.app.Module) extends RouterFactory {

    override def createRouter(): Router[Page] = {
      Router(BaseUrl.until_#, routerConfig)
    }

    private def routerConfig(implicit reactAppModule: flux.react.app.Module) = {
      RouterConfigDsl[Page].buildConfig { dsl =>
        import dsl._

        // wrap/connect components to the circuit
        (staticRoute(root, EverythingPage) ~> renderR(ctl => reactAppModule.everything())
          | staticRoute("#everything", EverythingPage2) ~> renderR(ctl => <.div(reactAppModule.everything(), reactAppModule.everything(), reactAppModule.everything()))
          | staticRoute("#new", NewTransactionGroupPage) ~> renderR(ctl => reactAppModule.transactionGroupForm())
          | staticRoute("#test", TestPage) ~> renderR(ctl => reactAppModule.menu(TestPage, ctl))
          ).notFound(redirectToPage(EverythingPage)(Redirect.Replace))
      }.renderWith(layout)
    }

    private def layout(c: RouterCtl[Page], r: Resolution[Page])(implicit reactAppModule: flux.react.app.Module) = {
      <.div(
        reactAppModule.menu(r.page, c),
        r.render()
      )
    }
  }
}
