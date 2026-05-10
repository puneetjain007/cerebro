package controllers

import javax.inject.Inject

import controllers.auth.AuthenticationModule
import controllers.auth.proxy.ProxyAuthConfig
import services.{AuditService, RoleService}
import elastic.ElasticClient
import models.{CerebroResponse, Hosts}

import scala.concurrent.ExecutionContext.Implicits.global

class CatController @Inject()(val authentication: AuthenticationModule,
                              val hosts: Hosts,
                                  val auditService: AuditService,
                                  val proxyConfig: ProxyAuthConfig,
                                  val rbacRoleService: RoleService,
                              client: ElasticClient) extends BaseController {

  def get = process { request =>
    val api = request.get("api")
    client.catRequest(api, request.target).map { response =>
      CerebroResponse(response.status, response.body)
    }
  }

}
