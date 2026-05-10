package controllers.auth

import controllers.auth.oauth.OAuthService
import controllers.auth.proxy.ProxyAuthConfig
import controllers.routes
import models.{CerebroResponse, User}
import play.api.libs.json.JsNull
import play.api.mvc._
import services.{AuditService, RoleService}

import scala.concurrent.{ExecutionContext, Future}

class AuthRequest[A](val user: Option[User], request: Request[A]) extends WrappedRequest[A](request)

final class AuthAction(
  auth: AuthenticationModule,
  redirect: Boolean,
  override val parser: BodyParser[AnyContent],
  oauthService: Option[OAuthService] = None,
  proxyAuthConfig: Option[ProxyAuthConfig] = None,
  roleService: Option[RoleService] = None,
  auditService: Option[AuditService] = None
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

  private def extractProxyUser[A](request: Request[A]): Option[User] = {
    for {
      cfg      <- proxyAuthConfig
      svc      <- roleService
      _        <- Option.when(cfg.isTrustedSource(request.remoteAddress))(())
      username <- request.headers.get(cfg.userHeader)
                    .orElse(request.headers.get(cfg.emailHeader))
    } yield {
      val groups = request.headers.get(cfg.groupsHeader)
        .map(_.split(",").map(_.trim).filter(_.nonEmpty).toSet)
        .getOrElse(Set.empty[String])
      val roles = svc.getRolesForGroups(groups)
      User(username, roles)
    }
  }

  def invokeBlock[A](request: Request[A], block: (AuthRequest[A]) => Future[Result]) = {
    if (auth.isEnabled) {
      // 1. Session-based auth (all modes)
      request.session.get(AuthAction.SESSION_USER).map { username =>
        val roles = request.session.get(AuthAction.SESSION_ROLES)
          .map(_.split(",").toSet)
          .getOrElse(Set.empty[String])
        block(new AuthRequest(Some(User(username, roles)), request))
      }.getOrElse {
        if (auth.isProxyEnabled) {
          // 2. Proxy mode: resolve user from forwarded headers
          val untrustedSource = proxyAuthConfig.exists(!_.isTrustedSource(request.remoteAddress))
          if (untrustedSource) {
            auditService.foreach(_.auditAuth(
              username  = "unknown",
              roles     = Set.empty,
              operation = "login",
              outcome   = "failure",
              sourceIp  = Some(request.remoteAddress),
              userAgent = request.headers.get("User-Agent")
            ))
            Future.successful(Results.Redirect(routes.AuthController.index()))
          } else {
            extractProxyUser(request) match {
              case Some(user) =>
                auditService.foreach(_.auditAuth(
                  username  = user.name,
                  roles     = user.roles,
                  operation = "login",
                  outcome   = "success",
                  sourceIp  = Some(request.remoteAddress),
                  userAgent = request.headers.get("User-Agent")
                ))
                block(new AuthRequest(Some(user), request))
              case None =>
                Future.successful(Results.Redirect(routes.AuthController.index()))
            }
          }
        } else {
          // 3. Bearer token (OAuth API clients)
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
      }
    } else {
      block(new AuthRequest(None, request))
    }
  }

  override protected def executionContext: ExecutionContext = ec
}

object AuthAction {

  private[controllers] val SESSION_USER  = "username"
  private[controllers] val SESSION_ROLES = "roles"
  private[controllers] val REDIRECT_URL  = "redirect"

}
