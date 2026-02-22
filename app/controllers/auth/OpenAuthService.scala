package controllers.auth

import models.User

class OpenAuthService extends AuthService {

  override def auth(username: String, password: String): Option[User] = None

}
