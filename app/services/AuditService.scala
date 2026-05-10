package services

import models.{AuditEvent, User}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.json._

import java.time.Instant
import javax.inject.{Inject, Singleton}

@Singleton
class AuditService @Inject()(config: Configuration) {

  private val logger = LoggerFactory.getLogger("audit")

  private val enabled = config.getOptional[Boolean]("audit.enabled").getOrElse(true)

  private val includeBody = config.getOptional[Boolean]("audit.include-body").getOrElse(true)

  private val excludedPaths: Set[String] =
    config.getOptional[Seq[String]]("audit.excluded-paths")
      .map(_.toSet)
      .getOrElse(Set("/overview", "/nodes", "/navbar", "/cluster_changes"))

  def audit(event: AuditEvent): Unit =
    if (enabled && !excludedPaths.contains(event.path))
      logger.info(toJson(event).toString)

  def auditAuth(
    username: String,
    roles: Set[String],
    operation: String,
    outcome: String,
    sourceIp: Option[String],
    userAgent: Option[String]
  ): Unit =
    if (enabled) {
      val json = Json.obj(
        "timestamp"  -> Instant.now().toString,
        "type"       -> "auth",
        "user"       -> username,
        "roles"      -> roles.mkString(","),
        "operation"  -> operation,
        "outcome"    -> outcome
      ) ++ sourceIp.fold(Json.obj())(ip => Json.obj("source_ip" -> ip)) ++
        userAgent.fold(Json.obj())(ua => Json.obj("user_agent" -> ua))
      logger.info(json.toString)
    }

  private def toJson(e: AuditEvent): JsObject = {
    val user     = e.user.map(_.name).getOrElse("anonymous")
    val roles    = e.user.map(_.roles.mkString(",")).getOrElse("")
    val base = Json.obj(
      "timestamp"      -> Instant.now().toString,
      "type"           -> "api",
      "user"           -> user,
      "roles"          -> roles,
      "http_method"    -> e.method,
      "path"           -> e.path,
      "operation"      -> e.operation,
      "outcome"        -> e.outcome
    )
    val withStatus     = e.statusCode.fold(base)(s => base ++ Json.obj("status_code" -> s))
    val withIp         = e.sourceIp.fold(withStatus)(ip => withStatus ++ Json.obj("source_ip" -> ip))
    val withUa         = e.userAgent.fold(withIp)(ua => withIp ++ Json.obj("user_agent" -> ua))
    val withHost       = e.targetHost.fold(withUa)(h => withUa ++ Json.obj("target_host" -> h))
    if (includeBody)
      e.body.fold(withHost)(b => withHost ++ Json.obj("body" -> b))
    else
      withHost
  }
}
