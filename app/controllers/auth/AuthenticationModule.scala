package controllers.auth

import com.google.inject.{ImplementedBy, Inject, Singleton}
import controllers.auth.basic.BasicAuthService
import controllers.auth.ldap.LDAPAuthService
import models.User
import play.api.Configuration

@ImplementedBy(classOf[AuthenticationModuleImpl])
trait AuthenticationModule {

  def authentication(username: String, password: String): Option[User]

  def isEnabled: Boolean

}

@Singleton
class AuthenticationModuleImpl @Inject()(
  config: Configuration,
  ldapAuthService: LDAPAuthService,
  basicAuthService: BasicAuthService
) extends AuthenticationModule {

  val service = config.getOptional[String]("auth.type") match {
    case Some("ldap")  => Some(ldapAuthService)
    case Some("basic") => Some(basicAuthService)
    case _             => None
  }

  def isEnabled: Boolean = {
    service.isDefined
  }

  def authentication(username: String, password: String): Option[User] = {
    service.getOrElse(throw new RuntimeException("No authentication modules is active")).auth(username, password)
  }

}
