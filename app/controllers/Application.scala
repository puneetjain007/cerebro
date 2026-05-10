package controllers

import com.google.inject.Inject
import controllers.auth.AuthenticationModule
import controllers.auth.proxy.ProxyAuthConfig
import play.api.mvc.InjectedController
import services.{AuditService, RoleService}

class Application @Inject()(
  val authentication: AuthenticationModule,
  val auditService: AuditService,
  val proxyConfig: ProxyAuthConfig,
  val rbacRoleService: RoleService
) extends InjectedController with AuthSupport {

  override protected def proxyAuthConfig = if (authentication.isProxyEnabled) Some(proxyConfig) else None
  override protected def roleService     = if (authentication.isProxyEnabled) Some(rbacRoleService) else None

  def index = AuthAction(authentication, true)(defaultExecutionContext) { request =>
    Ok(views.html.Index())
  }

}
