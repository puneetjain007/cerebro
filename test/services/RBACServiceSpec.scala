package services

import controllers.auth.ldap.LDAPRBACConfig
import models.User
import org.specs2.Specification
import org.specs2.mock.Mockito
import play.api.Configuration
import services.exception.InsufficientPermissionsException

class RBACServiceSpec extends Specification with Mockito {

  def is = s2"""
    RBACService should
      allow admin to perform all operations           $adminAllOps
      deny editor from cluster settings               $editorNoCluster
      deny editor from deleting indices               $editorNoDelete
      allow editor to write indices                   $editorCanWrite
      deny viewer from write operations               $viewerNoWrite
      allow all authenticated users to read           $allCanRead
      deny unauthenticated users all operations       $unauthDenied
      respect role hierarchy (admin > editor > viewer) $roleHierarchy
      fall back to restriction service when disabled  $fallbackWhenDisabled
      log access for audit trail                      $logAccess
  """

  def adminAllOps = {
    val (rbacService, _, _) = createServices(enabled = true)
    val admin = Some(User("admin-user", Set("admin")))

    rbacService.hasPermission(admin, ReadOperation) must beTrue
    rbacService.hasPermission(admin, WriteIndexOperation) must beTrue
    rbacService.hasPermission(admin, WriteClusterOperation) must beTrue
    rbacService.hasPermission(admin, AdminOperation) must beTrue
  }

  def editorNoCluster = {
    val (rbacService, _, _) = createServices(enabled = true)
    val editor = Some(User("editor-user", Set("editor")))

    rbacService.hasPermission(editor, WriteClusterOperation) must beFalse
    rbacService.requirePermission(editor, WriteClusterOperation) must throwA[InsufficientPermissionsException]
  }

  def editorNoDelete = {
    val (rbacService, _, _) = createServices(enabled = true)
    val editor = Some(User("editor-user", Set("editor")))

    rbacService.hasPermission(editor, AdminOperation) must beFalse
    rbacService.requirePermission(editor, AdminOperation) must throwA[InsufficientPermissionsException]
  }

  def editorCanWrite = {
    val (rbacService, _, _) = createServices(enabled = true)
    val editor = Some(User("editor-user", Set("editor")))

    rbacService.hasPermission(editor, WriteIndexOperation) must beTrue
    rbacService.requirePermission(editor, WriteIndexOperation) must not(throwA[InsufficientPermissionsException])
  }

  def viewerNoWrite = {
    val (rbacService, _, _) = createServices(enabled = true)
    val viewer = Some(User("viewer-user", Set("viewer")))

    rbacService.hasPermission(viewer, WriteIndexOperation) must beFalse
    rbacService.hasPermission(viewer, WriteClusterOperation) must beFalse
    rbacService.hasPermission(viewer, AdminOperation) must beFalse
  }

  def allCanRead = {
    val (rbacService, _, _) = createServices(enabled = true)
    val admin = Some(User("admin", Set("admin")))
    val editor = Some(User("editor", Set("editor")))
    val viewer = Some(User("viewer", Set("viewer")))

    rbacService.hasPermission(admin, ReadOperation) must beTrue
    rbacService.hasPermission(editor, ReadOperation) must beTrue
    rbacService.hasPermission(viewer, ReadOperation) must beTrue
  }

  def unauthDenied = {
    val (rbacService, _, _) = createServices(enabled = true)

    rbacService.hasPermission(None, ReadOperation) must beFalse
    rbacService.hasPermission(None, WriteIndexOperation) must beFalse
    rbacService.hasPermission(None, WriteClusterOperation) must beFalse
    rbacService.hasPermission(None, AdminOperation) must beFalse
  }

  def roleHierarchy = {
    val (rbacService, _, _) = createServices(enabled = true)
    val multiRole = Some(User("multi-user", Set("viewer", "editor", "admin")))

    // User with admin role can do everything
    rbacService.hasPermission(multiRole, AdminOperation) must beTrue
    rbacService.hasPermission(multiRole, WriteClusterOperation) must beTrue
    rbacService.hasPermission(multiRole, WriteIndexOperation) must beTrue
    rbacService.hasPermission(multiRole, ReadOperation) must beTrue
  }

  def fallbackWhenDisabled = {
    val (rbacService, restrictionService, _) = createServices(enabled = false, writeAllowed = true)
    val anyUser = Some(User("user", Set.empty))

    restrictionService.isWriteAllowed returns true
    rbacService.hasPermission(anyUser, WriteIndexOperation) must beTrue

    restrictionService.isWriteAllowed returns false
    rbacService.hasPermission(anyUser, WriteIndexOperation) must beFalse
  }

  def logAccess = {
    val (rbacService, _, _) = createServices(enabled = true)
    val user = Some(User("test-user", Set("admin")))

    // Should not throw
    rbacService.logAccess(user, ReadOperation, allowed = true) must not(throwAn[Exception])
    rbacService.logAccess(user, AdminOperation, allowed = false) must not(throwAn[Exception])
  }

  private def createServices(enabled: Boolean, writeAllowed: Boolean = true) = {
    val config = Configuration(
      "auth.settings.rbac.enabled" -> enabled,
      "auth.settings.rbac.role-mapping" -> "",
      "auth.settings.rbac.default-role" -> "none"
    )
    val rbacConfig = new LDAPRBACConfig(config)
    val roleService = new RoleService(rbacConfig)
    val restrictionService = mock[OperationRestrictionService]
    restrictionService.isWriteAllowed returns writeAllowed

    val rbacService = new RBACService(roleService, rbacConfig, restrictionService)
    (rbacService, restrictionService, roleService)
  }
}
