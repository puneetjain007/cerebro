package controllers.auth.oauth

import com.nimbusds.jose.jwk.source.{JWKSource, JWKSourceBuilder}
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext}
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.proc.{BadJWTException, DefaultJWTClaimsVerifier, DefaultJWTProcessor}
import com.nimbusds.jwt.JWTClaimsSet

import java.net.URL
import java.util
import javax.inject.{Inject, Singleton}
import play.api.Logging

@Singleton
class OAuthTokenValidator @Inject()(oauthConfig: OAuthConfig) extends Logging {

  private lazy val jwkSource: JWKSource[SecurityContext] = {
    JWKSourceBuilder.create[SecurityContext](new URL(oauthConfig.jwksUri)).build()
  }

  private lazy val jwtProcessor = {
    val processor = new DefaultJWTProcessor[SecurityContext]()

    val keySelector = new JWSVerificationKeySelector[SecurityContext](
      JWSAlgorithm.RS256,
      jwkSource
    )
    processor.setJWSKeySelector(keySelector)

    // Only enforce audience validation for id_token; access tokens
    // may not carry an aud claim matching the client ID.
    val requiredAudience = if (oauthConfig.tokenType == "id_token" && oauthConfig.clientId.nonEmpty) {
      new JWTClaimsSet.Builder().audience(oauthConfig.clientId).build()
    } else {
      null
    }
    val claimsVerifier = new DefaultJWTClaimsVerifier[SecurityContext](
      requiredAudience,
      new util.HashSet(util.Arrays.asList("sub", "exp", "iat"))
    )
    processor.setJWTClaimsSetVerifier(claimsVerifier)

    processor
  }

  def validate(token: String): Option[JWTClaimsSet] = {
    try {
      val claims = jwtProcessor.process(token, null)

      // Validate issuer if configured
      oauthConfig.issuer.foreach { expectedIssuer =>
        val actualIssuer = claims.getIssuer
        if (actualIssuer != expectedIssuer) {
          logger.warn(s"JWT issuer mismatch: expected=$expectedIssuer, actual=$actualIssuer")
          return None
        }
      }

      Some(claims)
    } catch {
      case e: BadJWTException =>
        logger.warn(s"JWT validation failed: ${e.getMessage}")
        None
      case e: Exception =>
        logger.error("JWT processing error", e)
        None
    }
  }

  def extractClaim(claims: JWTClaimsSet, claimName: String): Set[String] = {
    val parts = claimName.split("\\.")

    if (parts.length == 1) {
      extractSimpleClaim(claims, claimName)
    } else {
      extractNestedClaim(claims, parts.toList)
    }
  }

  private def extractSimpleClaim(claims: JWTClaimsSet, claimName: String): Set[String] = {
    val value = claims.getClaim(claimName)
    if (value == null) {
      Set.empty
    } else {
      value match {
        case list: java.util.List[_] =>
          import scala.jdk.CollectionConverters._
          list.asScala.map(_.toString).toSet
        case str: String =>
          Set(str)
        case other =>
          Set(other.toString)
      }
    }
  }

  private def extractNestedClaim(claims: JWTClaimsSet, parts: List[String]): Set[String] = {
    import scala.jdk.CollectionConverters._

    var current: Any = claims.toJSONObject

    for (part <- parts.dropRight(1)) {
      current match {
        case map: java.util.Map[_, _] =>
          current = map.get(part)
          if (current == null) return Set.empty
        case _ =>
          return Set.empty
      }
    }

    current match {
      case map: java.util.Map[_, _] =>
        val finalValue = map.get(parts.last)
        if (finalValue == null) {
          Set.empty
        } else {
          finalValue match {
            case list: java.util.List[_] =>
              list.asScala.map(_.toString).toSet
            case str: String =>
              Set(str)
            case other =>
              Set(other.toString)
          }
        }
      case _ =>
        Set.empty
    }
  }
}
