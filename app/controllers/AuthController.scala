package controllers

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import controllers.auth.{AuthAction, AuthenticationModule}
import forms.LoginForm
import play.api.Configuration
import play.api.mvc.InjectedController
import services.AuditService


@Singleton
class AuthController @Inject()(system: ActorSystem,
                               authentication: AuthenticationModule,
                               configuration: Configuration,
                               auditService: AuditService)
  extends InjectedController {

  import AuthController._

  private val badFormMsg = "invalid login form data"


  def index = Action { implicit request =>
    if (authentication.isEnabled) {
      request.session.get(AuthAction.SESSION_USER).map { user =>
        request.session.get(AuthAction.REDIRECT_URL) match {
          case Some(url) =>
            Redirect(url, play.api.http.Status.SEE_OTHER)
          case None =>
            Redirect(routes.Application.index())
        }
      }.getOrElse {
        if (authentication.isOAuthEnabled) {
          Redirect(routes.OAuthController.authorize())
        } else {
          Ok(views.html.auth.login())
        }
      }
    } else {
      Redirect(routes.Application.index())
    }
  }

  def login = Action { implicit request =>
    LoginForm.form.bindFromRequest().fold(
      formWithErrors => {
        log.error(badFormMsg)
        BadRequest(badFormMsg)
      },
      creds => {
        authentication.authentication(creds.user, creds.password) match {
          case Some(user) =>
            auditService.auditAuth(
              username  = user.name,
              roles     = user.roles,
              operation = "login",
              outcome   = "success",
              sourceIp  = Some(request.remoteAddress),
              userAgent = request.headers.get("User-Agent")
            )
            val resp =
              request.session.get(AuthAction.REDIRECT_URL) match {
                case Some(url) => Redirect(url, play.api.http.Status.SEE_OTHER)
                case None => Redirect(routes.Application.index())
              }
            resp.withSession(
              AuthAction.SESSION_USER -> user.name,
              AuthAction.SESSION_ROLES -> user.roles.mkString(",")
            )
          case None =>
            auditService.auditAuth(
              username  = creds.user,
              roles     = Set.empty,
              operation = "login",
              outcome   = "failure",
              sourceIp  = Some(request.remoteAddress),
              userAgent = request.headers.get("User-Agent")
            )
            Redirect(routes.AuthController.index()).flashing(LOGIN_MSG -> "Incorrect username or password")
        }
      }
    )
  }

  def logout = Action { implicit request =>
    val username = request.session.get(AuthAction.SESSION_USER).getOrElse("unknown")
    val roles    = request.session.get(AuthAction.SESSION_ROLES).map(_.split(",").toSet).getOrElse(Set.empty[String])
    auditService.auditAuth(
      username  = username,
      roles     = roles,
      operation = "logout",
      outcome   = "success",
      sourceIp  = Some(request.remoteAddress),
      userAgent = request.headers.get("User-Agent")
    )
    val prefix = configuration.getOptional[String]("play.http.context").getOrElse("/")
    Redirect(s"${prefix}login").withNewSession
  }

}

object AuthController {
  private val log = org.slf4j.LoggerFactory.getLogger(classOf[AuthController])

  final val LOGIN_MSG = "login-msg"
}
