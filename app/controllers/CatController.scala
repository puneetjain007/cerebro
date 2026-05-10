package controllers

import javax.inject.Inject

import controllers.auth.AuthenticationModule
import services.AuditService
import elastic.ElasticClient
import models.{CerebroResponse, Hosts}

import scala.concurrent.ExecutionContext.Implicits.global

class CatController @Inject()(val authentication: AuthenticationModule,
                              val hosts: Hosts,
                                  val auditService: AuditService,
                              client: ElasticClient) extends BaseController {

  def get = process { request =>
    val api = request.get("api")
    client.catRequest(api, request.target).map { response =>
      CerebroResponse(response.status, response.body)
    }
  }

}
