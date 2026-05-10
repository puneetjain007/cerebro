package controllers

import javax.inject.Inject

import controllers.auth.AuthenticationModule
import services.AuditService
import elastic.ElasticClient
import models.{CerebroResponse, Hosts}

import scala.concurrent.ExecutionContext.Implicits.global

class ClusterSettingsController @Inject()(val authentication: AuthenticationModule,
                                          val hosts: Hosts,
                                  val auditService: AuditService,
                                          client: ElasticClient) extends BaseController {

  def getSettings = process { request =>
    client.getClusterSettings(request.target).map { response =>
      CerebroResponse(response.status, response.body)
    }
  }

  def save = process { request =>
    val settings = request.getObj("settings")
    client.saveClusterSettings(settings, request.target, request.user).map { response =>
      CerebroResponse(response.status, response.body)
    }
  }

}
