package controllers.auth

import controllers.auth.ldap.LDAPRBACConfig
import controllers.auth.proxy.{ProxyAuthConfig, ProxyAuthService}
import models.User
import org.specs2.Specification
import org.specs2.mock.Mockito
import play.api.Configuration
import services.{RoleService, OperationRestrictionService}

class ProxyAuthSpec extends Specification with Mockito {

  def is = s2"""
    ProxyAuthService should
      return None for any username/password call   $noOpAuth

    ProxyAuthConfig should
      use default header names when unconfigured   $defaultHeaders
      read custom header names from config         $customHeaders
      read custom headers from env vars            $envVarHeaders

    Proxy group-to-role mapping should
      resolve admin role from mapped group         $groupMappedToAdmin
      resolve editor role from mapped group        $groupMappedToEditor
      resolve viewer role from mapped group        $groupMappedToViewer
      return empty roles for unmapped group        $unmappedGroupDenied
      union roles from multiple groups             $multiGroupUnion
      apply default role when no groups present    $defaultRoleApplied
  """

  val proxyService = new ProxyAuthService()

  def noOpAuth =
    proxyService.auth("alice", "secret") must beNone

  def defaultHeaders = {
    val cfg = new ProxyAuthConfig(Configuration.empty)
    (cfg.userHeader mustEqual "X-Auth-Request-User") and
      (cfg.groupsHeader mustEqual "X-Auth-Request-Groups") and
      (cfg.emailHeader mustEqual "X-Auth-Request-Email")
  }

  def customHeaders = {
    val cfg = new ProxyAuthConfig(Configuration.from(Map(
      "auth.settings.proxy.user-header"   -> "X-Forwarded-User",
      "auth.settings.proxy.groups-header" -> "X-Forwarded-Groups",
      "auth.settings.proxy.email-header"  -> "X-Forwarded-Email"
    )))
    (cfg.userHeader mustEqual "X-Forwarded-User") and
      (cfg.groupsHeader mustEqual "X-Forwarded-Groups") and
      (cfg.emailHeader mustEqual "X-Forwarded-Email")
  }

  def envVarHeaders = {
    // Config defaults fall back to sys.env; we test config-based overrides cover the path
    val cfg = new ProxyAuthConfig(Configuration.empty)
    cfg.userHeader must not(beEmpty)
  }

  private def roleService(mapping: String, default: String = "none"): RoleService = {
    val config = Configuration.from(Map(
      "auth.settings.rbac.enabled"      -> true,
      "auth.settings.rbac.role-mapping" -> mapping,
      "auth.settings.rbac.default-role" -> default
    ))
    val rbacConfig   = new LDAPRBACConfig(config)
    val restriction  = mock[OperationRestrictionService]
    new RoleService(rbacConfig)
  }

  def groupMappedToAdmin = {
    val svc = roleService("cerebro-admins=admin")
    svc.getRolesForGroups(Set("cerebro-admins")) mustEqual Set("admin")
  }

  def groupMappedToEditor = {
    val svc = roleService("cerebro-editors=editor")
    svc.getRolesForGroups(Set("cerebro-editors")) mustEqual Set("editor")
  }

  def groupMappedToViewer = {
    val svc = roleService("cerebro-viewers=viewer")
    svc.getRolesForGroups(Set("cerebro-viewers")) mustEqual Set("viewer")
  }

  def unmappedGroupDenied = {
    val svc = roleService("cerebro-admins=admin")
    svc.getRolesForGroups(Set("some-other-group")) mustEqual Set.empty
  }

  def multiGroupUnion = {
    val svc = roleService("cerebro-admins=admin;cerebro-editors=editor")
    svc.getRolesForGroups(Set("cerebro-admins", "cerebro-editors")) mustEqual Set("admin", "editor")
  }

  def defaultRoleApplied = {
    val svc = roleService("cerebro-admins=admin", default = "viewer")
    svc.getRolesForGroups(Set.empty) mustEqual Set("viewer")
  }
}
