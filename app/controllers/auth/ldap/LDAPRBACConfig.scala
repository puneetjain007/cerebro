package controllers.auth.ldap

import javax.inject.{Inject, Singleton}
import play.api.Configuration

@Singleton
class LDAPRBACConfig @Inject()(config: Configuration) {

  private val validRoles = Set("admin", "editor", "viewer")

  val enabled: Boolean = config.getOptional[Boolean]("auth.settings.rbac.enabled").getOrElse(false)

  val defaultRole: String = config.getOptional[String]("auth.settings.rbac.default-role").getOrElse("none")

  val roleMapping: Map[String, String] = {
    config.getOptional[String]("auth.settings.rbac.role-mapping") match {
      case Some(mappingStr) if mappingStr.nonEmpty =>
        parseMappingString(mappingStr)
      case _ =>
        Map.empty[String, String]
    }
  }

  private def parseMappingString(mappingStr: String): Map[String, String] = {
    mappingStr.split(";")
      .map(_.trim)
      .filter(_.nonEmpty)
      .flatMap { entry =>
        // Split on the LAST '=' to handle LDAP DNs containing '=' signs
        val lastEqualIndex = entry.lastIndexOf('=')
        if (lastEqualIndex > 0 && lastEqualIndex < entry.length - 1) {
          val group = entry.substring(0, lastEqualIndex).trim
          val role = entry.substring(lastEqualIndex + 1).trim
          if (group.nonEmpty && role.nonEmpty) {
            if (validRoles.contains(role)) {
              Some(group -> role)
            } else {
              throw new IllegalArgumentException(
                s"Invalid role '$role' in mapping. Valid roles are: ${validRoles.mkString(", ")}"
              )
            }
          } else {
            throw new IllegalArgumentException(
              s"Invalid mapping format: '$entry'. Expected format: 'ldap_group_dn=role'"
            )
          }
        } else {
          throw new IllegalArgumentException(
            s"Invalid mapping format: '$entry'. Expected format: 'ldap_group_dn=role'"
          )
        }
      }
      .toMap
  }

  def isEnabled: Boolean = enabled

  def getDefaultRole: Option[String] = {
    if (defaultRole == "none") None else Some(defaultRole)
  }
}
