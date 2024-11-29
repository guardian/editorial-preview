package com.gu.viewer.proxy

import lib.ConfigHelpers
import org.mockito.MockitoSugar
import org.scalatest.funsuite.AsyncFunSuite
import play.api.mvc.Cookie
import play.api.mvc.Results._
import play.api.test.Helpers._


class ProxyClientTest extends AsyncFunSuite with MockitoSugar with ConfigHelpers {

  val testUrl = "https://example.com/"

  test("can proxy a GET request without a custom handler and return the response") {
    val proxyClient = makeProxyClient {
      case (GET, userUrl) => Action {
        userUrl match {
          case `testUrl` => Ok
          case _ => NotFound
        }
      }
    }

    proxyClient.get(
      destination = testUrl,
      cookies = Seq(new Cookie(name = "test-name", value = "42")),
      headers = Seq.empty,
      queryString = Seq.empty,
      followRedirects = false)() map {
      result => {
        assert(result.isInstanceOf[ProxyResultWithBody])
        result match {
          case ProxyResultWithBody(response) => {
            response.status must be(200)
          }
          case _ => assert(result.isInstanceOf[ProxyResultWithBody])
        }
      }
    }
  }

}
