package controllers

import controllers.auth.{AuthAction, AuthenticationModule}
import controllers.auth.oauth.{OAuthConfig, OAuthService}
import play.api.Logging
import play.api.libs.ws.WSClient
import play.api.mvc.{Cookie, DiscardingCookie, InjectedController}

import java.security.SecureRandom
import java.util.Base64
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OAuthController @Inject()(
  oauthConfig: OAuthConfig,
  oauthService: OAuthService,
  ws: WSClient
)(implicit ec: ExecutionContext) extends InjectedController with Logging {

  private val secureRandom = new SecureRandom()
  private val OAuthStateCookie = "CEREBRO_OAUTH_STATE"
  private val OAuthNonceCookie = "CEREBRO_OAUTH_NONCE"

  private def generateState(): String = {
    val bytes = new Array[Byte](32)
    secureRandom.nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
  }

  def authorize() = Action { implicit request =>
    val state = generateState()
    val nonce = generateState()

    val params = Map(
      "client_id" -> oauthConfig.clientId,
      "response_type" -> "code",
      "redirect_uri" -> oauthConfig.redirectUri,
      "scope" -> oauthConfig.scopes,
      "state" -> state,
      "nonce" -> nonce
    )

    val queryString = params.map { case (k, v) =>
      s"${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
    }.mkString("&")

    val authUrl = s"${oauthConfig.authorizationEndpoint}?$queryString"

    val redirectUrl = request.session.get(AuthAction.REDIRECT_URL).getOrElse("/")

    Redirect(authUrl)
      .withSession(AuthAction.REDIRECT_URL -> redirectUrl)
      .withCookies(
        Cookie(OAuthStateCookie, state, httpOnly = true, path = "/"),
        Cookie(OAuthNonceCookie, nonce, httpOnly = true, path = "/")
      )
  }

  def callback(code: String, state: String) = Action.async { implicit request =>
    val cookieState = request.cookies.get(OAuthStateCookie).map(_.value)

    logger.debug(s"OAuth callback: state param=$state, cookie state=$cookieState")

    if (cookieState.isEmpty || !cookieState.contains(state)) {
      logger.warn(s"OAuth callback state mismatch - cookie present: ${cookieState.isDefined}")
      Future.successful(
        Redirect(routes.OAuthController.authorize())
      )
    } else {
      val tokenRequest = ws.url(oauthConfig.tokenEndpoint)
        .withHttpHeaders("Accept" -> "application/json")
        .post(Map(
          "grant_type" -> "authorization_code",
          "code" -> code,
          "redirect_uri" -> oauthConfig.redirectUri,
          "client_id" -> oauthConfig.clientId,
          "client_secret" -> oauthConfig.clientSecret
        ))

      tokenRequest.map { response =>
        if (response.status == 200) {
          val json = response.json
          val token = oauthConfig.tokenType match {
            case "access_token" => (json \ "access_token").asOpt[String]
            case _ => (json \ "id_token").asOpt[String]
          }

          token.flatMap(oauthService.validateAndExtractUser) match {
            case Some(user) =>
              val redirectUrl = request.session.get(AuthAction.REDIRECT_URL).getOrElse("/")
              logger.info(s"OAuth login successful for user ${user.name} with roles ${user.roles}")
              Redirect(redirectUrl).withSession(
                AuthAction.SESSION_USER -> user.name,
                AuthAction.SESSION_ROLES -> user.roles.mkString(",")
              ).discardingCookies(
                DiscardingCookie(OAuthStateCookie),
                DiscardingCookie(OAuthNonceCookie)
              )
            case None =>
              logger.warn("OAuth token validation failed")
              Redirect(routes.OAuthController.authorize())
                .discardingCookies(DiscardingCookie(OAuthStateCookie), DiscardingCookie(OAuthNonceCookie))
          }
        } else {
          logger.error(s"OAuth token exchange failed with status ${response.status}: ${response.body}")
          Redirect(routes.OAuthController.authorize())
            .discardingCookies(DiscardingCookie(OAuthStateCookie), DiscardingCookie(OAuthNonceCookie))
        }
      }.recover {
        case e: Exception =>
          logger.error("OAuth token exchange error", e)
          Redirect(routes.OAuthController.authorize())
            .discardingCookies(DiscardingCookie(OAuthStateCookie), DiscardingCookie(OAuthNonceCookie))
      }
    }
  }
}
