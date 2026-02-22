package controllers.auth.ldap

import java.util.Hashtable

import com.google.inject.Inject
import com.sun.jndi.ldap.LdapCtxFactory
import controllers.auth.AuthService
import models.User
import services.RoleService
import javax.naming._
import javax.naming.directory.{InitialDirContext, SearchControls}
import play.api.{Configuration, Logger}

import scala.collection.mutable
import scala.util.control.NonFatal

class LDAPAuthService @Inject()(
  globalConfig: Configuration,
  roleService: RoleService
) extends AuthService {

  private val log = Logger(this.getClass)

  private final val config = new LDAPAuthConfig(globalConfig.get[Configuration]("auth.settings"))

  def checkUserAuth(username: String, password: String): Boolean = {
    val props = new Hashtable[String, String]()
    props.put(Context.SECURITY_PRINCIPAL, config.userTemplate.format(username, config.baseDN))
    props.put(Context.SECURITY_CREDENTIALS, password)

    try {
      LdapCtxFactory.getLdapCtxInstance(config.url, props)
      true
    } catch {
      case e: AuthenticationException =>
        log.info(s"login of $username failed with: ${e.getMessage}")
        false
      case NonFatal(e) =>
        log.error(s"login of $username failed", e)
        false
    }
  }

  def checkGroupMembership(username: String, groupConfig: LDAPGroupSearchConfig): Boolean = {
    val props = new Hashtable[String, String]()
    props.put(Context.SECURITY_PRINCIPAL, groupConfig.bindDN)
    props.put(Context.SECURITY_CREDENTIALS, groupConfig.bindPwd)
    props.put(Context.REFERRAL, "follow")
    val user     = groupConfig.userAttrTemplate.format(username, config.baseDN)
    val controls = new SearchControls()
    controls.setSearchScope(SearchControls.SUBTREE_SCOPE)
    try {
      val context = LdapCtxFactory.getLdapCtxInstance(config.url, props)
      val search = context.search(groupConfig.baseDN,s"(& (${groupConfig.userAttr}=$user)(${groupConfig.group}))", controls)
      context.close()
      search.hasMore()
    } catch {
      case e: AuthenticationException =>
        log.info(s"User $username doesn't fulfill condition (${groupConfig.group}) : ${e.getMessage}")
        false
      case NonFatal(e) =>
        log.error(s"Unexpected error while checking group membership of $username", e)
        false
    }
  }

  /**
   * Retrieves all LDAP groups that the user belongs to.
   *
   * @param username The username
   * @return Set of LDAP group DNs
   */
  def getUserGroups(username: String): Set[String] = {
    config.groupMembership match {
      case Some(groupConfig) =>
        val props = new Hashtable[String, String]()
        props.put(Context.SECURITY_PRINCIPAL, groupConfig.bindDN)
        props.put(Context.SECURITY_CREDENTIALS, groupConfig.bindPwd)
        props.put(Context.REFERRAL, "follow")

        val userDN = config.userTemplate.format(username, config.baseDN)
        val searchFilter = s"(${groupConfig.userAttr}=$userDN)"
        val controls = new SearchControls()
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE)
        controls.setReturningAttributes(Array("dn"))

        try {
          val context = LdapCtxFactory.getLdapCtxInstance(config.url, props).asInstanceOf[InitialDirContext]
          val results = context.search(groupConfig.baseDN, searchFilter, controls)

          val groups = mutable.Set[String]()
          while (results.hasMore) {
            val result = results.next()
            val groupDN = result.getNameInNamespace
            groups += groupDN
          }
          context.close()
          groups.toSet
        } catch {
          case NonFatal(e) =>
            log.error(s"Error retrieving groups for user $username", e)
            Set.empty[String]
        }
      case None =>
        log.debug(s"Group search not configured, returning empty group set for user $username")
        Set.empty[String]
    }
  }

  def auth(username: String, password: String): Option[User] = {
    val isValidUser = config.groupMembership match {
      case Some(groupConfig) => checkGroupMembership(username, groupConfig) && checkUserAuth(username, password)
      case None              => checkUserAuth(username, password)
    }
    if (isValidUser) {
      val groups = getUserGroups(username)
      val roles = roleService.getRolesForGroups(groups)
      log.info(s"User $username authenticated with groups: ${groups.mkString(", ")} and roles: ${roles.mkString(", ")}")
      Some(User(username, roles))
    } else {
      None
    }
  }

}
