package controllers

import com.google.inject.Inject
import controllers.Auth.Forms
import models._
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._

class Auth @Inject()(val messagesApi: MessagesApi) extends Controller with I18nSupport{

  // ********** actions ********** //
  def login = Action { implicit request =>
    Ok(views.html.login(Forms.loginForm))
  }

  def authenticate = Action { implicit request =>
    Forms.loginForm.bindFromRequest.fold(
      formWithErrors => BadRequest(views.html.login(formWithErrors)),
      user => Redirect(routes.Application.index).withSession(Security.username -> user._1)
    )
  }

  def logout = Action { implicit request =>
    Redirect(routes.Auth.login).withNewSession.flashing(
      "message" -> Messages("facto.you-are-now-logged-out")
    )
  }
}

object Auth {
  // ********** forms ********** //
  object Forms {

    val loginForm = Form(
      tuple(
        "loginName" -> nonEmptyText,
        "password" -> text
      ) verifying("facto.error.invalid-username-or-password", result => result match {
        case (loginName, password) => Users.authenticate(loginName, password)
      })
    )
  }

}
