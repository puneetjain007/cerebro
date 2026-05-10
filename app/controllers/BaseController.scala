package controllers

import controllers.auth.{AuthRequest, AuthenticationModule}
import exceptions.MissingRequiredParamException
import models.{AuditEvent, CerebroRequest, CerebroResponse, Hosts}
import play.api.Logger
import play.api.libs.json.{JsSuccess, _}
import play.api.mvc.{InjectedController, Result}
import services.AuditService
import services.exception.{InsufficientPermissionsException, RequestFailedException}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

trait BaseController extends InjectedController with AuthSupport {

  val authentication: AuthenticationModule

  val hosts: Hosts

  val auditService: AuditService

  private val logger = Logger("application")

  type RequestProcessor = (CerebroRequest) => Future[Result]

  final def process(processor: RequestProcessor) = AuthAction(authentication).async(parse.json) { request =>
    val sourceIp  = Some(request.remoteAddress)
    val userAgent = request.headers.get("User-Agent")
    val censored  = request.body.transform(BodyCensor.censorPassword) match {
      case JsSuccess(v, _) => Some(v)
      case _               => Some(request.body)
    }

    def emitApiAudit(cerebroReq: Option[CerebroRequest], outcome: String, statusCode: Option[Int]): Unit =
      auditService.audit(AuditEvent(
        user       = cerebroReq.flatMap(_.user).orElse(request.user),
        method     = request.method,
        path       = request.path,
        operation  = "api_request",
        outcome    = outcome,
        statusCode = statusCode,
        sourceIp   = sourceIp,
        userAgent  = userAgent,
        targetHost = cerebroReq.map(_.target.host.name),
        body       = censored
      ))

    val parsedReq: Option[CerebroRequest] =
      scala.util.Try(CerebroRequest(request, hosts)).toOption

    try {
      processor(CerebroRequest(request, hosts)).map { result =>
        emitApiAudit(parsedReq, "allowed", Some(result.header.status))
        result
      }.recoverWith {
        case e: InsufficientPermissionsException =>
          logger.warn(s"Permission denied: user=${e.username}, operation=${e.operation}, required=${e.requiredRole}")
          emitApiAudit(parsedReq, "denied", Some(403))
          Future.successful(CerebroResponse(403, Json.obj(
            "error"         -> e.getMessage,
            "required_role" -> e.requiredRole
          )))
        case e: RequestFailedException =>
          emitApiAudit(parsedReq, "error", Some(e.status))
          Future.successful(CerebroResponse(e.status, Json.obj("error" -> e.getMessage)))
        case NonFatal(e) =>
          logger.error(s"Error processing request [${formatRequest(request)}]", e)
          emitApiAudit(parsedReq, "error", Some(500))
          Future.successful(CerebroResponse(500, Json.obj("error" -> e.getMessage)))
      }
    } catch {
      case e: InsufficientPermissionsException =>
        logger.warn(s"Permission denied: user=${e.username}, operation=${e.operation}, required=${e.requiredRole}")
        emitApiAudit(parsedReq, "denied", Some(403))
        Future.successful(CerebroResponse(403, Json.obj(
          "error"         -> e.getMessage,
          "required_role" -> e.requiredRole
        )))
      case e: MissingRequiredParamException =>
        Future.successful(CerebroResponse(400, Json.obj("error" -> e.getMessage)))
      case NonFatal(e) =>
        logger.error(s"Error processing request [${formatRequest(request)}]", e)
        emitApiAudit(parsedReq, "error", Some(500))
        Future.successful(CerebroResponse(500, Json.obj("error" -> e.getMessage)))
    }
  }

  private def formatRequest(request: AuthRequest[JsValue]): String = {
    val body = request.body.transform(BodyCensor.censorPassword) getOrElse request.body
    s"path: ${request.uri}, body: ${body.toString()}"
  }

}

object BodyCensor {
  val censorPassword: Reads[JsObject] =
    (__ \ "password").json.update(__.read[JsString].map(_ => JsString("xxxxxx")))
}
