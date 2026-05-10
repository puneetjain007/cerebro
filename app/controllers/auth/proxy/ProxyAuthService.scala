package controllers.auth.proxy

import controllers.auth.AuthService
import models.User

import javax.inject.{Inject, Singleton}

// Proxy mode does not use username/password — user principal comes from request headers.
// This service exists only to satisfy the AuthService interface and signal proxy mode is active.
@Singleton
class ProxyAuthService @Inject()() extends AuthService {
  def auth(username: String, password: String): Option[User] = None
}
