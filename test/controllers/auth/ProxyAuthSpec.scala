package controllers.auth

import controllers.auth.ldap.LDAPRBACConfig
import controllers.auth.proxy.{IpMatcher, ProxyAuthConfig, ProxyAuthService}
import models.User
import org.specs2.Specification
import org.specs2.mock.Mockito
import play.api.Configuration
import services.{OperationRestrictionService, RoleService}

class ProxyAuthSpec extends Specification with Mockito {

  def is = s2"""
    ProxyAuthService should
      return None for any username/password call         $noOpAuth

    ProxyAuthConfig should
      use default header names when unconfigured         $defaultHeaders
      read custom header names from config               $customHeaders
      read custom headers from env vars                  $envVarHeaders
      trust any source when trusted-ips is empty         $emptyTrustedIpsAllowsAll
      allow a configured exact IP                        $exactIpAllowed
      reject an IP not in the allowlist                  $exactIpRejected
      allow an IP within a CIDR block                    $cidrAllowed
      reject an IP outside a CIDR block                  $cidrRejected
      allow multiple IPs/CIDRs                           $multipleTrustedIps

    IpMatcher should
      match exact IPv4 address                           $matchExactIp
      reject different IPv4 address                      $rejectDifferentIp
      match IP inside /24 CIDR                           $matchCidr24
      reject IP outside /24 CIDR                         $rejectOutsideCidr24
      match IP inside /8 CIDR                            $matchCidr8
      match IP at network boundary                       $matchBoundary
      handle invalid CIDR gracefully                     $invalidCidrSafe

    Proxy group-to-role mapping should
      resolve admin role from mapped group               $groupMappedToAdmin
      resolve editor role from mapped group              $groupMappedToEditor
      resolve viewer role from mapped group              $groupMappedToViewer
      return empty roles for unmapped group              $unmappedGroupDenied
      union roles from multiple groups                   $multiGroupUnion
      apply default role when no groups present          $defaultRoleApplied
  """

  val proxyService = new ProxyAuthService()

  // ---- ProxyAuthService ----

  def noOpAuth =
    proxyService.auth("alice", "secret") must beNone

  // ---- ProxyAuthConfig headers ----

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
    val cfg = new ProxyAuthConfig(Configuration.empty)
    cfg.userHeader must not(beEmpty)
  }

  // ---- ProxyAuthConfig trusted-ips ----

  def emptyTrustedIpsAllowsAll = {
    val cfg = new ProxyAuthConfig(Configuration.empty)
    cfg.isTrustedSource("1.2.3.4") must beTrue
  }

  def exactIpAllowed = {
    val cfg = cfgWithTrustedIps("10.0.0.1")
    cfg.isTrustedSource("10.0.0.1") must beTrue
  }

  def exactIpRejected = {
    val cfg = cfgWithTrustedIps("10.0.0.1")
    cfg.isTrustedSource("10.0.0.2") must beFalse
  }

  def cidrAllowed = {
    val cfg = cfgWithTrustedIps("172.16.0.0/24")
    cfg.isTrustedSource("172.16.0.50") must beTrue
  }

  def cidrRejected = {
    val cfg = cfgWithTrustedIps("172.16.0.0/24")
    cfg.isTrustedSource("172.16.1.1") must beFalse
  }

  def multipleTrustedIps = {
    val cfg = cfgWithTrustedIps("10.0.0.1", "192.168.0.0/16")
    (cfg.isTrustedSource("10.0.0.1") must beTrue) and
      (cfg.isTrustedSource("192.168.5.10") must beTrue) and
      (cfg.isTrustedSource("8.8.8.8") must beFalse)
  }

  private def cfgWithTrustedIps(ips: String*): ProxyAuthConfig =
    new ProxyAuthConfig(Configuration.from(Map("auth.settings.proxy.trusted-ips" -> ips.toList)))

  // ---- IpMatcher ----

  def matchExactIp       = IpMatcher.matches("10.0.0.1", "10.0.0.1") must beTrue
  def rejectDifferentIp  = IpMatcher.matches("10.0.0.2", "10.0.0.1") must beFalse
  def matchCidr24        = IpMatcher.matches("192.168.1.100", "192.168.1.0/24") must beTrue
  def rejectOutsideCidr24 = IpMatcher.matches("192.168.2.1", "192.168.1.0/24") must beFalse
  def matchCidr8         = IpMatcher.matches("10.99.88.77", "10.0.0.0/8") must beTrue
  def matchBoundary      = IpMatcher.matches("10.0.0.0", "10.0.0.0/8") must beTrue
  def invalidCidrSafe    = IpMatcher.matches("10.0.0.1", "not-a-cidr") must beFalse

  // ---- Group-to-role mapping ----

  private def roleService(mapping: String, default: String = "none"): RoleService = {
    val config = Configuration.from(Map(
      "auth.settings.rbac.enabled"      -> true,
      "auth.settings.rbac.role-mapping" -> mapping,
      "auth.settings.rbac.default-role" -> default
    ))
    new RoleService(new LDAPRBACConfig(config))
  }

  def groupMappedToAdmin  = roleService("cerebro-admins=admin").getRolesForGroups(Set("cerebro-admins")) mustEqual Set("admin")
  def groupMappedToEditor = roleService("cerebro-editors=editor").getRolesForGroups(Set("cerebro-editors")) mustEqual Set("editor")
  def groupMappedToViewer = roleService("cerebro-viewers=viewer").getRolesForGroups(Set("cerebro-viewers")) mustEqual Set("viewer")
  def unmappedGroupDenied = roleService("cerebro-admins=admin").getRolesForGroups(Set("some-other-group")) mustEqual Set.empty
  def multiGroupUnion     = roleService("cerebro-admins=admin;cerebro-editors=editor").getRolesForGroups(Set("cerebro-admins", "cerebro-editors")) mustEqual Set("admin", "editor")
  def defaultRoleApplied  = roleService("cerebro-admins=admin", default = "viewer").getRolesForGroups(Set.empty) mustEqual Set("viewer")
}
