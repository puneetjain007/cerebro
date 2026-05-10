package controllers

import controllers.auth.{AuthAction, AuthenticationModule}
import controllers.auth.proxy.ProxyAuthConfig
import play.api.mvc.InjectedController
import services.{AuditService, RoleService}

import scala.concurrent.ExecutionContext

trait AuthSupport { self: InjectedController =>

  val auditService: AuditService

  def AuthAction(authentication: AuthenticationModule, redirect: Boolean = false)(implicit ec: ExecutionContext): AuthAction = {
    val (proxyCfg, roleSvc) =
      if (authentication.isProxyEnabled) (proxyAuthConfig, roleService)
      else (None, None)
    new AuthAction(
      auth            = authentication,
      redirect        = redirect,
      parser          = parse.anyContent,
      oauthService    = authentication.oauthService,
      proxyAuthConfig = proxyCfg,
      roleService     = roleSvc,
      auditService    = Some(auditService)
    )
  }

  // Provided by Guice via BaseController subclasses when proxy mode is active.
  // Defaulted to None so non-proxy deployments require no config changes.
  protected def proxyAuthConfig: Option[ProxyAuthConfig] = None
  protected def roleService: Option[RoleService] = None

}
