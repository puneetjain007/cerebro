package controllers.auth

import controllers.auth.oauth.OAuthService
import controllers.routes
import models.{CerebroResponse, User}
import play.api.libs.json.JsNull
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class AuthRequest[A](val user: Option[User], request: Request[A]) extends WrappedRequest[A](request)

final class AuthAction(
  auth: AuthenticationModule,
  redirect: Boolean,
  override val parser: BodyParser[AnyContent],
  oauthService: Option[OAuthService] = None
)(implicit ec: ExecutionContext)
  extends ActionBuilder[AuthRequest, AnyContent] {

  private val BearerPattern = """(?i)Bearer\s+(.+)""".r

  private def extractBearerUser[A](request: Request[A]): Option[User] = {
    for {
      header  <- request.headers.get("Authorization")
      token   <- header match {
        case BearerPattern(t) => Some(t.trim)
        case _                => None
      }
      service <- oauthService
      user    <- service.validateAndExtractUser(token)
    } yield user
  }

  def invokeBlock[A](request: Request[A], block: (AuthRequest[A]) => Future[Result]) = {
    if (auth.isEnabled) {
      request.session.get(AuthAction.SESSION_USER).map { username =>
        val roles = request.session.get(AuthAction.SESSION_ROLES)
          .map(_.split(",").toSet)
          .getOrElse(Set.empty[String])
        block(new AuthRequest(Some(User(username, roles)), request))
      }.getOrElse {
        extractBearerUser(request) match {
          case Some(user) =>
            block(new AuthRequest(Some(user), request))
          case None =>
            if (redirect) {
              val target = if (auth.isOAuthEnabled) {
                routes.OAuthController.authorize()
              } else {
                routes.AuthController.index()
              }
              Future.successful(
                Results.Redirect(target).
                  withSession(AuthAction.REDIRECT_URL -> request.uri))
            } else {
              val hasBearerToken = request.headers.get("Authorization").exists(_.toLowerCase.startsWith("bearer "))
              if (hasBearerToken) {
                Future.successful(Results.Unauthorized.withHeaders("WWW-Authenticate" -> "Bearer"))
              } else {
                Future.successful(CerebroResponse(303, JsNull))
              }
            }
        }
      }
    } else {
      block(new AuthRequest(None, request))
    }
  }

  override protected def executionContext: ExecutionContext = ec
}

object AuthAction {

  private[controllers] val SESSION_USER = "username"
  private[controllers] val SESSION_ROLES = "roles"
  private[controllers] val REDIRECT_URL = "redirect"

}