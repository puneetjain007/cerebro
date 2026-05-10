package controllers

import controllers.auth.AuthenticationModule
import controllers.auth.proxy.ProxyAuthConfig
import elastic.ElasticClient
import org.specs2.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.BeforeEach
import play.api.Configuration
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsValue
import play.api.mvc.Result
import play.api.test.Helpers.{contentAsJson, _}
import services.AuditService

import scala.concurrent.Future

trait MockedServices extends Specification with BeforeEach with Mockito {

  val client = mock[ElasticClient]

  val auth = mock[AuthenticationModule]
  auth.isEnabled returns false
  auth.isProxyEnabled returns false

  val auditService = new AuditService(Configuration.from(Map("audit.enabled" -> false)))
  val proxyAuthConfig = new ProxyAuthConfig(Configuration.empty)

  override def before = {
    org.mockito.Mockito.reset(client)
  }

  val application = new GuiceApplicationBuilder().
    overrides(
      bind[ElasticClient].toInstance(client),
      bind[AuthenticationModule].toInstance(auth),
      bind[AuditService].toInstance(auditService),
      bind[ProxyAuthConfig].toInstance(proxyAuthConfig)
    ).build()

  def ensure(response: Future[Result], statusCode: Int, body: JsValue) = {
    ((contentAsJson(response) \ "body").as[JsValue] mustEqual body) and
      ((contentAsJson(response) \ "status").as[Int] mustEqual statusCode)
  }

}
