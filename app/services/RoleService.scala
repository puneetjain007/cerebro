package services

import controllers.auth.ldap.LDAPRBACConfig
import models.User

import javax.inject.{Inject, Singleton}

@Singleton
class RoleService @Inject()(config: LDAPRBACConfig) {

  private val roleHierarchy = Map(
    "admin" -> 3,
    "editor" -> 2,
    "viewer" -> 1
  )

  /**
   * Maps LDAP groups to Cerebro roles using the configured mapping.
   * Returns deny-by-default: unmapped groups result in no roles.
   *
   * @param ldapGroups Set of LDAP group DNs the user belongs to
   * @return Set of Cerebro roles (union of all mapped roles)
   */
  def getRolesForGroups(ldapGroups: Set[String]): Set[String] = {
    if (!config.isEnabled) {
      // When RBAC is disabled, return admin role for backward compatibility
      return Set("admin")
    }

    val mappedRoles = ldapGroups.flatMap(group => config.roleMapping.get(group))

    if (mappedRoles.isEmpty) {
      // User has no mapped groups - apply default role
      config.getDefaultRole.toSet
    } else {
      mappedRoles
    }
  }

  /**
   * Checks if a user can perform an operation based on their roles.
   *
   * @param user User with roles
   * @param requiredRole Minimum role required for the operation
   * @return true if user has sufficient permissions
   */
  def canPerformOperation(user: Option[User], requiredRole: String): Boolean = {
    if (!config.isEnabled) {
      // When RBAC is disabled, all authenticated users can perform all operations
      return user.isDefined
    }

    user match {
      case Some(u) if u.roles.nonEmpty =>
        val userMaxLevel = u.roles.flatMap(roleHierarchy.get).maxOption.getOrElse(0)
        val requiredLevel = roleHierarchy.getOrElse(requiredRole, Int.MaxValue)
        userMaxLevel >= requiredLevel
      case _ =>
        false
    }
  }
}
