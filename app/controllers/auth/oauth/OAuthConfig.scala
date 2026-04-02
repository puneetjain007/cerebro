package controllers.auth.oauth

import java.nio.file.{Files, Paths}
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Logging}
import play.api.libs.ws.WSClient

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

@Singleton
class OAuthConfig @Inject()(config: Configuration, ws: WSClient)(implicit ec: ExecutionContext) extends Logging {

  private val oauthConfig = config.getOptional[Configuration]("auth.settings.oauth")

  val isConfigured: Boolean = {
    oauthConfig.flatMap(_.getOptional[String]("client-id")).isDefined
  }

  // Use lazy vals so config is only evaluated when OAuth is actually used.
  // This prevents Guice injection failures when OAuth is not configured
  // but the routes still reference OAuthController.

  private lazy val discoveredEndpoints: Option[DiscoveredEndpoints] = {
    oauthConfig.flatMap { c =>
      c.getOptional[String]("discovery-uri").map { uri =>
        logger.info(s"Fetching OIDC discovery document from $uri")
        try {
          val response = Await.result(ws.url(uri).get(), 10.seconds)
          val json = response.json
          DiscoveredEndpoints(
            authorizationEndpoint = (json \ "authorization_endpoint").as[String],
            tokenEndpoint = (json \ "token_endpoint").as[String],
            jwksUri = (json \ "jwks_uri").as[String],
            issuer = (json \ "issuer").asOpt[String]
          )
        } catch {
          case e: Exception =>
            logger.error(s"Failed to fetch OIDC discovery document from $uri", e)
            throw new RuntimeException(s"Failed to fetch OIDC discovery document: ${e.getMessage}", e)
        }
      }
    }
  }

  lazy val clientId: String = getSecretOrRequired("client-id", "client-id-file")

  lazy val clientSecret: String = getSecretOrRequired("client-secret", "client-secret-file")

  lazy val redirectUri: String = getRequired("redirect-uri")

  lazy val scopes: String = getString("scopes", "openid profile email groups")

  lazy val tokenType: String = getString("token-type", "id_token")

  lazy val claimMapping: String = getString("claim-mapping", "groups")

  lazy val authorizationEndpoint: String = {
    getOptional("authorization-endpoint")
      .orElse(discoveredEndpoints.map(_.authorizationEndpoint))
      .getOrElse(throw new RuntimeException("OAuth authorization-endpoint not configured and discovery failed"))
  }

  lazy val tokenEndpoint: String = {
    getOptional("token-endpoint")
      .orElse(discoveredEndpoints.map(_.tokenEndpoint))
      .getOrElse(throw new RuntimeException("OAuth token-endpoint not configured and discovery failed"))
  }

  lazy val jwksUri: String = {
    getOptional("jwks-uri")
      .orElse(discoveredEndpoints.map(_.jwksUri))
      .getOrElse(throw new RuntimeException("OAuth jwks-uri not configured and discovery failed"))
  }

  lazy val issuer: Option[String] = {
    getOptional("issuer").orElse(discoveredEndpoints.flatMap(_.issuer))
  }

  /**
   * Reads a secret value with three-tier resolution:
   *   1. File path (e.g., client-secret-file = "/run/secrets/oauth_secret")
   *   2. Inline value (e.g., client-secret = "value")
   *   3. Throws if neither is set
   *
   * File-based secrets take precedence over inline values, supporting
   * Docker secrets (/run/secrets/...) and Kubernetes secret volumes.
   */
  private def getSecretOrRequired(inlineKey: String, fileKey: String): String = {
    val fromFile = getOptional(fileKey).flatMap { path =>
      val p = Paths.get(path)
      if (Files.exists(p) && Files.isReadable(p)) {
        val value = new String(Files.readAllBytes(p)).trim
        if (value.nonEmpty) {
          logger.info(s"Loaded OAuth '$inlineKey' from file: $path")
          Some(value)
        } else {
          logger.warn(s"OAuth secret file '$path' is empty, falling back to inline config")
          None
        }
      } else {
        logger.warn(s"OAuth secret file '$path' not found or not readable, falling back to inline config")
        None
      }
    }
    fromFile.orElse(getOptional(inlineKey))
      .getOrElse(throw new RuntimeException(
        s"OAuth config 'auth.settings.oauth.$inlineKey' is required (set inline or via '$fileKey')"
      ))
  }

  private def getRequired(key: String): String = {
    oauthConfig
      .flatMap(_.getOptional[String](key))
      .getOrElse(throw new RuntimeException(s"OAuth config 'auth.settings.oauth.$key' is required"))
  }

  private def getOptional(key: String): Option[String] = {
    oauthConfig.flatMap(_.getOptional[String](key))
  }

  private def getString(key: String, default: String): String = {
    oauthConfig.flatMap(_.getOptional[String](key)).getOrElse(default)
  }
}

case class DiscoveredEndpoints(
  authorizationEndpoint: String,
  tokenEndpoint: String,
  jwksUri: String,
  issuer: Option[String]
)
