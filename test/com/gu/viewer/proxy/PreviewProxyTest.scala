package com.gu.viewer.proxy

import lib.ConfigHelpers
import org.mockito.MockitoSugar
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.must.Matchers
import play.api.mvc.Results._
import play.api.mvc.Session
import play.api.test.Helpers.GET

import scala.concurrent.ExecutionContext

class PreviewProxyTest extends AsyncFunSuite with Matchers with MockitoSugar with ConfigHelpers{

  val supportedServicePath = "item/42"
  val requestHost = "example.com"

  val supportedProxyUrl = s"https://${devAppConfig.previewHost}/$supportedServicePath"
  val loginUrlForTest = s"https://${devAppConfig.previewHost}/login"

  val createPreviewProxy: PreviewProxy = {

    val mockProxyClient = makeProxyClient {
      case (GET, userUrl) => Action {
        userUrl match {
          case `supportedProxyUrl` => Ok
          case _ => NotFound
        }
      }
    }

    new PreviewProxy(
      mockProxyClient,
      devAppConfig,
    )(ExecutionContext.Implicits.global)
  }

  def createSession: PreviewSession = {
    new PreviewSession(
      sessionCookie = Some("my-session-cookie"),
      authCookie = None,
      returnUrl = None,
      playSession = new Session
    )
  }

  def createRequest(path: String): PreviewProxyRequest = {
    new PreviewProxyRequest(
      servicePath = path,
      requestHost = requestHost,
      requestUri = s"https://$requestHost/$path",
      requestQueryString = Map.empty,
      session = createSession
    )
  }

  test("can construct login url") {
    val instance = createPreviewProxy
    instance.previewLoginUrl must be(loginUrlForTest)
  }

  test("will proxy requests to the configured preview host") {
    val instance = createPreviewProxy
    val request = createRequest(supportedServicePath)

    instance.proxy(request) map {
      result => assert(result.header.status === 200)
    }
  }
}
