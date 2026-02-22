package controllers.auth

import models.User

trait AuthService {

  def auth(username: String, password: String): Option[User]

}
