package services

import controllers.auth.ldap.LDAPRBACConfig
import models.User
import play.api.Logging
import services.exception.InsufficientPermissionsException

import javax.inject.{Inject, Singleton}

sealed trait OperationType {
  val requiredRole: String
  val operationName: String
}

case object ReadOperation extends OperationType {
  val requiredRole = "viewer"
  val operationName = "read"
}

case object WriteIndexOperation extends OperationType {
  val requiredRole = "editor"
  val operationName = "write_index"
}

case object WriteClusterOperation extends OperationType {
  val requiredRole = "admin"
  val operationName = "write_cluster"
}

case object AdminOperation extends OperationType {
  val requiredRole = "admin"
  val operationName = "admin"
}

@Singleton
class RBACService @Inject()(
  roleService: RoleService,
  config: LDAPRBACConfig,
  restrictionService: OperationRestrictionService
) extends Logging {

  /**
   * Requires that the user has permission to perform the specified operation.
   * Throws InsufficientPermissionsException if permission is denied.
   *
   * @param user User attempting the operation
   * @param operation Operation type with required role
   * @throws InsufficientPermissionsException if user lacks permission
   */
  def requirePermission(user: Option[User], operation: OperationType): Unit = {
    if (!hasPermission(user, operation)) {
      val username = user.map(_.name).getOrElse("anonymous")
      logger.warn(
        s"Permission denied: user=$username, operation=${operation.operationName}, " +
        s"required_role=${operation.requiredRole}, user_roles=${user.map(_.roles).getOrElse(Set.empty).mkString(",")}"
      )
      throw InsufficientPermissionsException(
        username,
        operation.operationName,
        operation.requiredRole
      )
    }
  }

  /**
   * Checks if the user has permission to perform the specified operation.
   *
   * @param user User attempting the operation
   * @param operation Operation type with required role
   * @return true if user has permission
   */
  def hasPermission(user: Option[User], operation: OperationType): Boolean = {
    if (!config.isEnabled) {
      // When RBAC is disabled, fall back to global read-only mode
      // If operation is read, allow it; otherwise check restriction service
      operation match {
        case ReadOperation => true
        case _ => restrictionService.isWriteAllowed
      }
    } else {
      // RBAC is enabled - check user roles
      operation match {
        case ReadOperation =>
          // All authenticated users can read
          user.isDefined
        case _ =>
          // Write operations require specific roles
          roleService.canPerformOperation(user, operation.requiredRole)
      }
    }
  }

  /**
   * Logs access for audit trail.
   *
   * @param user User performing the operation
   * @param operation Operation being performed
   * @param allowed Whether the operation was allowed
   */
  def logAccess(user: Option[User], operation: OperationType, allowed: Boolean): Unit = {
    val username = user.map(_.name).getOrElse("anonymous")
    val userRoles = user.map(_.roles).getOrElse(Set.empty).mkString(",")
    val status = if (allowed) "ALLOWED" else "DENIED"

    logger.info(
      s"Access: status=$status, user=$username, roles=[$userRoles], " +
      s"operation=${operation.operationName}, required=${operation.requiredRole}"
    )
  }
}
