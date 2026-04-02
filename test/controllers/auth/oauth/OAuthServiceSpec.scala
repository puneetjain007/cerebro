package controllers.auth.oauth

import com.nimbusds.jwt.JWTClaimsSet
import controllers.auth.ldap.LDAPRBACConfig
import models.User
import org.specs2.Specification
import org.specs2.mock.Mockito
import play.api.Configuration
import services.RoleService

class OAuthServiceSpec extends Specification with Mockito {

  def is = s2"""
    OAuthService should
      extract roles from array claim values                $arrayClaimRoles
      extract role from string claim value                 $stringClaimRole
      apply default role for unmapped claims               $defaultRoleForUnmapped
      return None for invalid token                        $invalidToken
      extract username from sub claim                      $usernameFromSub
      extract username from preferred_username             $usernameFromPreferred
      extract username from email                          $usernameFromEmail
      return None when no username claim present           $noUsernameClaim
      extract nested claim via dot notation                $nestedClaimExtraction
      handle empty claim values                            $emptyClaimValues
  """

  private def createRoleService(
    enabled: Boolean = true,
    mapping: String = "cerebro-admins=admin;cerebro-editors=editor;cerebro-viewers=viewer",
    defaultRole: String = "none"
  ): RoleService = {
    val config = Configuration(
      "auth.settings.rbac.enabled" -> enabled,
      "auth.settings.rbac.role-mapping" -> mapping,
      "auth.settings.rbac.default-role" -> defaultRole
    )
    new RoleService(new LDAPRBACConfig(config))
  }

  private def createMockValidator(claimName: String, resultClaims: Option[JWTClaimsSet] = None): OAuthTokenValidator = {
    val validator = mock[OAuthTokenValidator]

    // Default: any token validation returns the provided claims
    resultClaims match {
      case Some(claims) =>
        validator.validate(anyString) returns Some(claims)
        // Delegate extractClaim to a real implementation for testing
        validator.extractClaim(any[JWTClaimsSet], anyString) answers { (args: Array[AnyRef]) =>
          val c = args(0).asInstanceOf[JWTClaimsSet]
          val name = args(1).asInstanceOf[String]
          realExtractClaim(c, name)
        }
      case None =>
        validator.validate(anyString) returns None
    }
    validator
  }

  private def realExtractClaim(claims: JWTClaimsSet, claimName: String): Set[String] = {
    import scala.jdk.CollectionConverters._

    if (claimName.contains(".")) {
      val parts = claimName.split("\\.")
      var current: Any = claims.toJSONObject
      for (part <- parts.dropRight(1)) {
        current match {
          case map: java.util.Map[_, _] =>
            current = map.get(part)
            if (current == null) return Set.empty
          case _ => return Set.empty
        }
      }
      current match {
        case map: java.util.Map[_, _] =>
          val finalValue = map.get(parts.last)
          if (finalValue == null) Set.empty
          else finalValue match {
            case list: java.util.List[_] => list.asScala.map(_.toString).toSet
            case str: String => Set(str)
            case other => Set(other.toString)
          }
        case _ => Set.empty
      }
    } else {
      val value = claims.getClaim(claimName)
      if (value == null) Set.empty
      else value match {
        case list: java.util.List[_] => list.asScala.map(_.toString).toSet
        case str: String => Set(str)
        case other => Set(other.toString)
      }
    }
  }

  private def createOAuthService(
    validator: OAuthTokenValidator,
    claimMapping: String = "groups",
    roleMapping: String = "cerebro-admins=admin;cerebro-editors=editor;cerebro-viewers=viewer",
    defaultRole: String = "none"
  ): OAuthService = {
    val oauthConfig = mock[OAuthConfig]
    oauthConfig.claimMapping returns claimMapping
    val roleService = createRoleService(enabled = true, mapping = roleMapping, defaultRole = defaultRole)
    new OAuthService(validator, oauthConfig, roleService)
  }

  def arrayClaimRoles = {
    val claims = new JWTClaimsSet.Builder()
      .subject("testuser")
      .claim("groups", java.util.Arrays.asList("cerebro-admins", "cerebro-editors"))
      .build()

    val validator = createMockValidator("groups", Some(claims))
    val service = createOAuthService(validator)
    val result = service.validateAndExtractUser("valid-token")

    result must beSome
    result.get.name must beEqualTo("testuser")
    result.get.roles must contain("admin")
    result.get.roles must contain("editor")
  }

  def stringClaimRole = {
    val claims = new JWTClaimsSet.Builder()
      .subject("testuser")
      .claim("role", "cerebro-admins")
      .build()

    val validator = createMockValidator("role", Some(claims))
    val service = createOAuthService(validator, claimMapping = "role")
    val result = service.validateAndExtractUser("valid-token")

    result must beSome
    result.get.roles must beEqualTo(Set("admin"))
  }

  def defaultRoleForUnmapped = {
    val claims = new JWTClaimsSet.Builder()
      .subject("testuser")
      .claim("groups", java.util.Arrays.asList("unknown-group"))
      .build()

    val validator = createMockValidator("groups", Some(claims))
    val service = createOAuthService(validator, defaultRole = "viewer")
    val result = service.validateAndExtractUser("valid-token")

    result must beSome
    result.get.roles must beEqualTo(Set("viewer"))
  }

  def invalidToken = {
    val validator = createMockValidator("groups", None)
    val service = createOAuthService(validator)
    val result = service.validateAndExtractUser("invalid-token")

    result must beNone
  }

  def usernameFromSub = {
    val claims = new JWTClaimsSet.Builder()
      .subject("user123")
      .claim("groups", java.util.Arrays.asList("cerebro-viewers"))
      .build()

    val validator = createMockValidator("groups", Some(claims))
    val service = createOAuthService(validator)
    val result = service.validateAndExtractUser("valid-token")

    result must beSome
    result.get.name must beEqualTo("user123")
  }

  def usernameFromPreferred = {
    val claims = new JWTClaimsSet.Builder()
      .claim("preferred_username", "jdoe")
      .claim("groups", java.util.Arrays.asList("cerebro-viewers"))
      .build()

    val validator = createMockValidator("groups", Some(claims))
    val service = createOAuthService(validator)
    val result = service.validateAndExtractUser("valid-token")

    result must beSome
    result.get.name must beEqualTo("jdoe")
  }

  def usernameFromEmail = {
    val claims = new JWTClaimsSet.Builder()
      .claim("email", "jdoe@example.com")
      .claim("groups", java.util.Arrays.asList("cerebro-viewers"))
      .build()

    val validator = createMockValidator("groups", Some(claims))
    val service = createOAuthService(validator)
    val result = service.validateAndExtractUser("valid-token")

    result must beSome
    result.get.name must beEqualTo("jdoe@example.com")
  }

  def noUsernameClaim = {
    val claims = new JWTClaimsSet.Builder()
      .claim("groups", java.util.Arrays.asList("cerebro-viewers"))
      .build()

    val validator = createMockValidator("groups", Some(claims))
    val service = createOAuthService(validator)
    val result = service.validateAndExtractUser("valid-token")

    result must beNone
  }

  def nestedClaimExtraction = {
    val nested = new java.util.HashMap[String, Object]()
    nested.put("roles", java.util.Arrays.asList("cerebro-admins"))

    val claims = new JWTClaimsSet.Builder()
      .subject("testuser")
      .claim("app_metadata", nested)
      .build()

    val validator = createMockValidator("app_metadata.roles", Some(claims))
    val service = createOAuthService(validator, claimMapping = "app_metadata.roles")
    val result = service.validateAndExtractUser("valid-token")

    result must beSome
    result.get.roles must beEqualTo(Set("admin"))
  }

  def emptyClaimValues = {
    val claims = new JWTClaimsSet.Builder()
      .subject("testuser")
      .claim("groups", new java.util.ArrayList[String]())
      .build()

    val validator = createMockValidator("groups", Some(claims))
    val service = createOAuthService(validator, defaultRole = "viewer")
    val result = service.validateAndExtractUser("valid-token")

    result must beSome
    result.get.roles must beEqualTo(Set("viewer"))
  }
}
