package models

import play.api.libs.json.JsValue

case class AuditEvent(
  user: Option[User],
  method: String,
  path: String,
  operation: String,
  outcome: String,
  statusCode: Option[Int],
  sourceIp: Option[String],
  userAgent: Option[String],
  targetHost: Option[String],
  body: Option[JsValue]
)
