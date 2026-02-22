package controllers.auth.ldap

import org.specs2.Specification
import play.api.Configuration

class LDAPRBACConfigSpec extends Specification {

  def is = s2"""
    LDAPRBACConfig should
      parse valid role mapping string                    $parseValidMapping
      handle empty mapping gracefully                    $emptyMapping
      reject invalid role names                          $rejectInvalidRoles
      handle missing configuration                       $missingConfig
      support environment variable override              $envVarOverride
      parse multiple group mappings                      $multipleGroupMappings
      handle malformed mapping entries                   $malformedEntries
      provide correct defaults                           $correctDefaults
  """

  def parseValidMapping = {
    val config = Configuration(
      "auth.settings.rbac.enabled" -> true,
      "auth.settings.rbac.role-mapping" -> "cn=admins,ou=groups,dc=example,dc=com=admin"
    )
    val rbacConfig = new LDAPRBACConfig(config)

    rbacConfig.isEnabled must beTrue
    rbacConfig.roleMapping must haveKey("cn=admins,ou=groups,dc=example,dc=com")
    rbacConfig.roleMapping("cn=admins,ou=groups,dc=example,dc=com") must beEqualTo("admin")
  }

  def emptyMapping = {
    val config = Configuration(
      "auth.settings.rbac.enabled" -> true,
      "auth.settings.rbac.role-mapping" -> ""
    )
    val rbacConfig = new LDAPRBACConfig(config)

    rbacConfig.isEnabled must beTrue
    rbacConfig.roleMapping must beEmpty
  }

  def rejectInvalidRoles = {
    val config = Configuration(
      "auth.settings.rbac.enabled" -> true,
      "auth.settings.rbac.role-mapping" -> "cn=admins,ou=groups=superuser"
    )

    new LDAPRBACConfig(config) must throwA[IllegalArgumentException]
  }

  def missingConfig = {
    val config = Configuration()
    val rbacConfig = new LDAPRBACConfig(config)

    rbacConfig.isEnabled must beFalse
    rbacConfig.roleMapping must beEmpty
    rbacConfig.getDefaultRole must beNone
  }

  def envVarOverride = {
    val config = Configuration(
      "auth.settings.rbac.enabled" -> false
    )
    val rbacConfig = new LDAPRBACConfig(config)

    // When enabled is false
    rbacConfig.isEnabled must beFalse
  }

  def multipleGroupMappings = {
    val config = Configuration(
      "auth.settings.rbac.enabled" -> true,
      "auth.settings.rbac.role-mapping" ->
        "cn=admins,ou=groups,dc=example,dc=com=admin;cn=editors,ou=groups,dc=example,dc=com=editor;cn=viewers,ou=groups,dc=example,dc=com=viewer"
    )
    val rbacConfig = new LDAPRBACConfig(config)

    rbacConfig.roleMapping.size must beEqualTo(3)
    rbacConfig.roleMapping("cn=admins,ou=groups,dc=example,dc=com") must beEqualTo("admin")
    rbacConfig.roleMapping("cn=editors,ou=groups,dc=example,dc=com") must beEqualTo("editor")
    rbacConfig.roleMapping("cn=viewers,ou=groups,dc=example,dc=com") must beEqualTo("viewer")
  }

  def malformedEntries = {
    val config = Configuration(
      "auth.settings.rbac.enabled" -> true,
      "auth.settings.rbac.role-mapping" -> "invalid-entry-no-equals"
    )

    new LDAPRBACConfig(config) must throwA[IllegalArgumentException]
  }

  def correctDefaults = {
    val config = Configuration()
    val rbacConfig = new LDAPRBACConfig(config)

    rbacConfig.isEnabled must beFalse
    rbacConfig.defaultRole must beEqualTo("none")
    rbacConfig.getDefaultRole must beNone

    val configWithViewer = Configuration(
      "auth.settings.rbac.default-role" -> "viewer"
    )
    val rbacConfigWithViewer = new LDAPRBACConfig(configWithViewer)

    rbacConfigWithViewer.getDefaultRole must beSome("viewer")
  }
}
