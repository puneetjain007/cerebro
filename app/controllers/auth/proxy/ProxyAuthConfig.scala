package controllers.auth.proxy

import javax.inject.{Inject, Singleton}
import play.api.Configuration

@Singleton
class ProxyAuthConfig @Inject()(config: Configuration) {

  val userHeader: String =
    config.getOptional[String]("auth.settings.proxy.user-header")
      .orElse(sys.env.get("CEREBRO_PROXY_USER_HEADER"))
      .getOrElse("X-Auth-Request-User")

  val groupsHeader: String =
    config.getOptional[String]("auth.settings.proxy.groups-header")
      .orElse(sys.env.get("CEREBRO_PROXY_GROUPS_HEADER"))
      .getOrElse("X-Auth-Request-Groups")

  val emailHeader: String =
    config.getOptional[String]("auth.settings.proxy.email-header")
      .orElse(sys.env.get("CEREBRO_PROXY_EMAIL_HEADER"))
      .getOrElse("X-Auth-Request-Email")

  // Allowlist of source IPs (or CIDR blocks) permitted to supply proxy headers.
  // Empty set = no IP restriction (unsafe — only use when firewalled at the network layer).
  val trustedIps: Set[String] =
    config.getOptional[Seq[String]]("auth.settings.proxy.trusted-ips")
      .map(_.toSet)
      .orElse(sys.env.get("CEREBRO_PROXY_TRUSTED_IPS").map(_.split(",").map(_.trim).filter(_.nonEmpty).toSet))
      .getOrElse(Set.empty[String])

  def isTrustedSource(remoteAddress: String): Boolean =
    trustedIps.isEmpty || trustedIps.exists(cidr => IpMatcher.matches(remoteAddress, cidr))

}
