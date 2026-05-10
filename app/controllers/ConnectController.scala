package controllers

import javax.inject.Inject

import controllers.auth.AuthenticationModule
import controllers.auth.proxy.ProxyAuthConfig
import elastic.ElasticClient
import models.{CerebroRequest, CerebroResponse, Hosts}
import play.api.libs.json.{JsArray, JsString}
import play.api.mvc.InjectedController
import services.{AuditService, RoleService}

import scala.concurrent.ExecutionContext.Implicits.global

class ConnectController @Inject()(
  val authentication: AuthenticationModule,
  val auditService: AuditService,
  val proxyConfig: ProxyAuthConfig,
  val rbacRoleService: RoleService,
  elastic: ElasticClient,
  hosts: Hosts
) extends InjectedController with AuthSupport {

  override protected def proxyAuthConfig = if (authentication.isProxyEnabled) Some(proxyConfig) else None
  override protected def roleService     = if (authentication.isProxyEnabled) Some(rbacRoleService) else None

  def index = AuthAction(authentication)(defaultExecutionContext) { _ =>
    CerebroResponse(200, JsArray(hosts.getHostNames().map(JsString(_))))
  }

  def connect = AuthAction(authentication)(defaultExecutionContext).async(parse.json) { request =>
    val req = CerebroRequest(request, hosts)
    elastic.executeRequest("GET", "_cluster/health", None, req.target, req.user).map {
      response => CerebroResponse(response.status, response.body)
    }
  }

}
