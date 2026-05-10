package controllers.auth

import com.google.inject.{ImplementedBy, Inject, Singleton}
import controllers.auth.basic.BasicAuthService
import controllers.auth.ldap.LDAPAuthService
import controllers.auth.oauth.OAuthService
import controllers.auth.proxy.ProxyAuthService
import models.User
import play.api.Configuration

@ImplementedBy(classOf[AuthenticationModuleImpl])
trait AuthenticationModule {

  def authentication(username: String, password: String): Option[User]

  def isEnabled: Boolean

  def isOAuthEnabled: Boolean

  def isProxyEnabled: Boolean

  def oauthService: Option[OAuthService]

}

@Singleton
class AuthenticationModuleImpl @Inject()(
  config: Configuration,
  ldapAuthService: LDAPAuthService,
  basicAuthService: BasicAuthService,
  oauthSvc: OAuthService,
  proxyAuthService: ProxyAuthService
) extends AuthenticationModule {

  private val authType: Option[String] = config.getOptional[String]("auth.type")

  val service: Option[AuthService] = authType match {
    case Some("ldap")  => Some(ldapAuthService)
    case Some("basic") => Some(basicAuthService)
    case Some("oauth") => Some(oauthSvc)
    case Some("proxy") => Some(proxyAuthService)
    case _             => None
  }

  def isEnabled: Boolean = service.isDefined

  def isOAuthEnabled: Boolean = authType.contains("oauth")

  def isProxyEnabled: Boolean = authType.contains("proxy")

  def oauthService: Option[OAuthService] = {
    if (isOAuthEnabled) Some(oauthSvc) else None
  }

  def authentication(username: String, password: String): Option[User] = {
    service.getOrElse(throw new RuntimeException("No authentication modules is active")).auth(username, password)
  }

}
