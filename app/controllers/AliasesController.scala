package controllers

import javax.inject.Inject

import controllers.auth.AuthenticationModule
import controllers.auth.proxy.ProxyAuthConfig
import services.{AuditService, RoleService}
import elastic.{ElasticClient, Error, Success}
import models.{Aliases, CerebroResponse, Hosts}
import play.api.libs.json.JsArray

import scala.concurrent.ExecutionContext.Implicits.global

class AliasesController @Inject()(val authentication: AuthenticationModule,
                                  val hosts: Hosts,
                                  val auditService: AuditService,
                                  val proxyConfig: ProxyAuthConfig,
                                  val rbacRoleService: RoleService,
                                  client: ElasticClient) extends BaseController {

  def getAliases = process { request =>
    client.getAliases(request.target).map {
      case Success(status, aliases) => CerebroResponse(status, Aliases(aliases))
      case Error(status, error) => CerebroResponse(status, error)
    }
  }

  def updateAliases = process { request =>
    val changes = request.getOptArray("changes").getOrElse(JsArray()).value.toSeq
    client.updateAliases(changes, request.target, request.user).map { aliases =>
      CerebroResponse(aliases.status, aliases.body)
    }
  }

}
