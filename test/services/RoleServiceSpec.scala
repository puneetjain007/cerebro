package services

import controllers.auth.ldap.LDAPRBACConfig
import models.User
import org.specs2.Specification
import play.api.Configuration

class RoleServiceSpec extends Specification {

  def is = s2"""
    RoleService should
      map LDAP groups to roles correctly                 $mapGroupsToRoles
      return empty set for unmapped groups (deny by default) $denyByDefault
      combine multiple groups into union of roles        $multipleGroupsUnion
      handle case-sensitive group names                  $caseSensitiveGroups
      return admin role when RBAC disabled               $adminWhenDisabled
      apply default role when no groups match            $defaultRole
      respect role hierarchy in canPerformOperation      $roleHierarchyCheck
  """

  def mapGroupsToRoles = {
    val roleService = createRoleService(
      enabled = true,
      mapping = "cn=admins,ou=groups=admin;cn=editors,ou=groups=editor;cn=viewers,ou=groups=viewer"
    )

    val adminGroups = Set("cn=admins,ou=groups")
    val editorGroups = Set("cn=editors,ou=groups")
    val viewerGroups = Set("cn=viewers,ou=groups")

    roleService.getRolesForGroups(adminGroups) must beEqualTo(Set("admin"))
    roleService.getRolesForGroups(editorGroups) must beEqualTo(Set("editor"))
    roleService.getRolesForGroups(viewerGroups) must beEqualTo(Set("viewer"))
  }

  def denyByDefault = {
    val roleService = createRoleService(
      enabled = true,
      mapping = "cn=admins,ou=groups=admin"
    )

    val unmappedGroups = Set("cn=unknown,ou=groups", "cn=other,ou=groups")
    roleService.getRolesForGroups(unmappedGroups) must beEqualTo(Set.empty)
  }

  def multipleGroupsUnion = {
    val roleService = createRoleService(
      enabled = true,
      mapping = "cn=admins,ou=groups=admin;cn=editors,ou=groups=editor;cn=viewers,ou=groups=viewer"
    )

    val multipleGroups = Set("cn=admins,ou=groups", "cn=editors,ou=groups")
    val roles = roleService.getRolesForGroups(multipleGroups)

    roles must contain("admin")
    roles must contain("editor")
    roles.size must beEqualTo(2)
  }

  def caseSensitiveGroups = {
    val roleService = createRoleService(
      enabled = true,
      mapping = "cn=Admins,ou=groups=admin"
    )

    // Exact match
    roleService.getRolesForGroups(Set("cn=Admins,ou=groups")) must beEqualTo(Set("admin"))

    // Different case - should not match (LDAP DNs are case-sensitive)
    roleService.getRolesForGroups(Set("cn=admins,ou=groups")) must beEqualTo(Set.empty)
  }

  def adminWhenDisabled = {
    val roleService = createRoleService(enabled = false, mapping = "")

    val anyGroups = Set("cn=unknown,ou=groups")
    roleService.getRolesForGroups(anyGroups) must beEqualTo(Set("admin"))
  }

  def defaultRole = {
    val roleService = createRoleService(
      enabled = true,
      mapping = "cn=admins,ou=groups=admin",
      defaultRole = "viewer"
    )

    val unmappedGroups = Set("cn=unknown,ou=groups")
    roleService.getRolesForGroups(unmappedGroups) must beEqualTo(Set("viewer"))
  }

  def roleHierarchyCheck = {
    val roleService = createRoleService(
      enabled = true,
      mapping = "cn=admins,ou=groups=admin;cn=editors,ou=groups=editor;cn=viewers,ou=groups=viewer"
    )

    val admin = Some(User("admin", Set("admin")))
    val editor = Some(User("editor", Set("editor")))
    val viewer = Some(User("viewer", Set("viewer")))
    val noRole = Some(User("norole", Set.empty))

    // Admin can do everything
    roleService.canPerformOperation(admin, "admin") must beTrue
    roleService.canPerformOperation(admin, "editor") must beTrue
    roleService.canPerformOperation(admin, "viewer") must beTrue

    // Editor can do editor and viewer operations
    roleService.canPerformOperation(editor, "admin") must beFalse
    roleService.canPerformOperation(editor, "editor") must beTrue
    roleService.canPerformOperation(editor, "viewer") must beTrue

    // Viewer can only do viewer operations
    roleService.canPerformOperation(viewer, "admin") must beFalse
    roleService.canPerformOperation(viewer, "editor") must beFalse
    roleService.canPerformOperation(viewer, "viewer") must beTrue

    // No role can't do anything
    roleService.canPerformOperation(noRole, "viewer") must beFalse
  }

  private def createRoleService(enabled: Boolean, mapping: String, defaultRole: String = "none") = {
    val config = Configuration(
      "auth.settings.rbac.enabled" -> enabled,
      "auth.settings.rbac.role-mapping" -> mapping,
      "auth.settings.rbac.default-role" -> defaultRole
    )
    val rbacConfig = new LDAPRBACConfig(config)
    new RoleService(rbacConfig)
  }
}
