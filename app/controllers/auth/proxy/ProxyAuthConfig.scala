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

}
