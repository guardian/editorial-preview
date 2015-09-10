package com.gu.viewer.controllers

import com.gu.viewer.config.Configuration
import com.gu.viewer.proxy.{Proxy => NewProxy}
import com.gu.viewer.views.html
import java.net.URLEncoder
import javax.inject.Inject
import com.gu.viewer.logging.Loggable
import play.api.libs.concurrent.Execution.Implicits._
import play.api.Play.current
import play.api.libs.ws.{WSResponse, WSClient, WSRequest}
import play.api.mvc.{Controller, Cookie, Cookies, Action, Result, RequestHeader}
import scala.concurrent.Future


class Proxy @Inject() (newProxy: NewProxy) extends Controller with Loggable {

  private val COOKIE_PREVIEW_SESSION = "PLAY_SESSION"
  private val COOKIE_PREVIEW_AUTH = "GU_PV_AUTH"

  private val SESSION_KEY_PREVIEW_SESSION = "preview-session"
  private val SESSION_KEY_PREVIEW_AUTH = "preview-auth"
  private val SESSION_KEY_RETURN_URL = "preview-auth-return-url"

  val previewLoginUrl = s"http://${Configuration.previewHost}/login"

  def loginCallbackUrl(implicit request: RequestHeader) = {
    val protocol = if (request.secure) "https" else "http"
    s"$protocol://${request.host}${routes.Proxy.previewAuthCallback()}"
  }

  /**
   * Transform proxy server relative URI to viewer URI.
   */
  def proxyUriToViewerUri(uri: String) = {
    val proxyUri = """^\/proxy(\/.+)$""".r

    uri match {
      case proxyUri(path) => Some(path)
      case _ => None
    }
  }

  def proxy(service: String, path: String) = Action.async { implicit request =>
    val protocol = if (request.secure) "https" else "http"
    val serviceHost = service match {
      case "preview" => Configuration.previewHost
      case _ => Configuration.liveHost
    }
    val url = s"$protocol://$serviceHost/$path"


    def doPreviewAuth() = {
      val proxyRequestUrl = previewLoginUrl

      log.info(s"Proxy Preview auth to: $proxyRequestUrl")

      def handleResponse(response: WSResponse) = {

        // store new preview session from response
        val cookies = Cookies.fromSetCookieHeader(response.header("Set-Cookie"))
        val previewSessionOpt = cookies.get(COOKIE_PREVIEW_SESSION).map( c => SESSION_KEY_PREVIEW_SESSION -> c.value )

        val loc = response.header("Location").get

        previewSessionOpt match {
          case Some(session) => {
            val returnUrl = SESSION_KEY_RETURN_URL -> proxyUriToViewerUri(request.uri).getOrElse(request.uri)
            Ok(html.loginRedirect(loc))
              .withSession(request.session - SESSION_KEY_PREVIEW_SESSION - SESSION_KEY_PREVIEW_AUTH + session + returnUrl)
          }

          case None => badGatewayResponse("Unexpected response from preview login request", response)
        }
      }

      newProxy.ProxyRequest(proxyRequestUrl, queryString = Seq("redirect-url" -> loginCallbackUrl)).post() {
        case response if response.status == 303 => Future.successful(handleResponse(response))
        // TODO should we handle non redirect responses here?
      }
    }


    def doPreviewProxy(session: String, auth: String) = {
      log.info(s"Proxy to preview: $url")

      val cookies = Seq(COOKIE_PREVIEW_SESSION -> session, COOKIE_PREVIEW_AUTH -> auth).map(c => Cookie(c._1, c._2))

      /* TODO handle redirects with proxy
          case (status, Some(otherLocation)) => {
            log.warn(s"Proxied response for $url is $status redirect to: $otherLocation")
            Future.successful(Status(status).withHeaders("Location" -> otherLocation))
          }
      */

      def isLoginRedirect(response: WSResponse) = {
        response.status == 303 &&
        response.header("Location").exists(l => l == previewLoginUrl || l == "/login")
      }

      newProxy.ProxyRequest(url, cookies = cookies).get {
        case response if isLoginRedirect(response) => doPreviewAuth()
      }

    }


    def doProxy() = {
      log.info(s"Proxy to: $url")

      // TODO rewrite redirects to proxied URLS
      newProxy.ProxyRequest(url, followRedirects = true).get()
    }


    if (service == "preview") {
      request.session.get(SESSION_KEY_PREVIEW_SESSION) -> request.session.get(SESSION_KEY_PREVIEW_AUTH) match {
        case (Some(session), Some(auth)) => doPreviewProxy(session, auth)
        case _ => doPreviewAuth()
      }

    } else {
      // live
      doProxy()
    }

  }


  /**
   * Preview Authentication callback.
   *
   * Proxy all request params and Preview session cookie to Preview authentication callback.
   * Store response cookies into Viewer's play session.
   */
  def previewAuthCallback = Action.async { implicit request =>

    val queryParams = request.queryString.flatMap( q => q._2.map { v => q._1 -> v } ).toSeq :+ ("redirect-url" -> loginCallbackUrl)

    def handleResponse(response: WSResponse): Result = {
      val newCookies: Map[String, Cookie] = response.allHeaders.get("Set-Cookie") match {
        case Some(setCookieHeader) => setCookieHeader
          .flatMap { h => Cookies.fromSetCookieHeader(Some(h)) }
          .groupBy(_.name)
          .mapValues(_.head)

        case None => Map.empty
      }

      val responseCookies = Cookies.fromCookieHeader(response.header("Cookie"))

      val sessionOpt = newCookies.get(COOKIE_PREVIEW_SESSION)
        .orElse(responseCookies.get(COOKIE_PREVIEW_SESSION))
        .map(c => SESSION_KEY_PREVIEW_SESSION -> c.value)

      val authOpt = newCookies.get(COOKIE_PREVIEW_AUTH).map(c => SESSION_KEY_PREVIEW_AUTH -> c.value)

      (sessionOpt, authOpt) match {
        case (Some(sessionValue), Some(authValue)) => {
          val returnUrl = request.session.get(SESSION_KEY_RETURN_URL).getOrElse("/proxy/preview/uk")
          Redirect(returnUrl)
            .withSession(request.session - SESSION_KEY_RETURN_URL + sessionValue + authValue)
        }
        case (None, None) => badGatewayResponse("Bad response from preview auth callback", response)
        case (None, _) => badGatewayResponse("Preview Session cookie not returned", response)
        case (_, None) => badGatewayResponse("Preview Auth cookie not returned", response)
      }
    }

    request.session.get(SESSION_KEY_PREVIEW_SESSION) match {
      case None => Future.successful(BadRequest("Preview session not established"))

      case Some(session) => {
        val proxyUrl = s"http://${Configuration.previewHost}/oauth2callback"
        log.info(s"Proxy preview auth callback to: $proxyUrl")

        val cookies = Seq(Cookie(COOKIE_PREVIEW_SESSION, session))

        newProxy.ProxyRequest(proxyUrl, queryString = queryParams, cookies = cookies).get {
          case r => Future.successful(handleResponse(r))
        }
      }
    }
  }


  /**
   * Redirect requests to routes that don't exist which originate (Referer header)
   * from a proxied request. Catches server relative requests in a proxied response.
   */
  def redirectRelative(path: String) = Action { request =>

    val fromProxy = """^\w+:\/\/([^/]+)\/proxy\/([^/]+).*$""".r
    val host = request.host

    request.headers.get("Referer") match {
      case Some(fromProxy(`host`, service)) =>
        Redirect(routes.Proxy.proxy(service, request.uri.tail))

      case _ => NotFound(s"Resource not found: $path")
    }
  }

  private def badGatewayResponse(msg: String, response: WSResponse) = {
    log.warn(s"$msg: ${response.toString} ${response.allHeaders} ${response.body}")
    BadGateway(msg)
  }
}
