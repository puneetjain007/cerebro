package controllers.auth.oauth

import controllers.auth.AuthService
import models.User
import services.RoleService

import javax.inject.{Inject, Singleton}
import play.api.Logging

@Singleton
class OAuthService @Inject()(
  tokenValidator: OAuthTokenValidator,
  oauthConfig: OAuthConfig,
  roleService: RoleService
) extends AuthService with Logging {

  /**
   * OAuth does not use username/password authentication.
   * Authentication happens via the OAuth redirect flow in OAuthController.
   */
  override def auth(username: String, password: String): Option[User] = None

  /**
   * Validates a JWT token and extracts the user with mapped roles.
   *
   * @param token The JWT token string (ID token or access token based on config)
   * @return Some(User) if token is valid, None otherwise
   */
  def validateAndExtractUser(token: String): Option[User] = {
    tokenValidator.validate(token).flatMap { claims =>
      val username = Option(claims.getSubject).orElse {
        Option(claims.getStringClaim("preferred_username"))
      }.orElse {
        Option(claims.getStringClaim("email"))
      }

      username match {
        case Some(name) =>
          val claimValues = tokenValidator.extractClaim(claims, oauthConfig.claimMapping)
          val roles = roleService.getRolesForGroups(claimValues)
          logger.info(s"OAuth user $name authenticated with claim values: ${claimValues.mkString(", ")} and roles: ${roles.mkString(", ")}")
          Some(User(name, roles))
        case None =>
          logger.warn("JWT token has no subject, preferred_username, or email claim")
          None
      }
    }
  }
}
