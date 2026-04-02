package controllers.auth

import com.google.inject.ImplementedBy
import controllers.auth.ldap.LDAPRBACConfig

@ImplementedBy(classOf[LDAPRBACConfig])
trait RBACConfig {

  def isEnabled: Boolean

  def roleMapping: Map[String, String]

  def getDefaultRole: Option[String]

}
