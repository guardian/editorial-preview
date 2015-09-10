package com.gu.viewer.proxy

import javax.inject.{Inject, Singleton}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.{WSResponse, WSClient}
import play.api.mvc.{Cookies, Cookie, Result}
import play.api.mvc.Results.Status
import play.api.http.HeaderNames.{CONTENT_LENGTH, CONTENT_TYPE, COOKIE}
import play.api.http.MimeTypes.TEXT

import scala.concurrent.Future

@Singleton
class Proxy @Inject()(ws: WSClient) {

  private val TIMEOUT = 10000

  private def proxy(
             method: String,
             destination: String,
             headers: Seq[(String, String)] = Seq.empty,
             cookies: Seq[Cookie] = Seq.empty,
             queryString: Seq[(String, String)] = Seq.empty,
             body: Map[String, Seq[String]] = Map.empty,
             followRedirects: Boolean = false
             )(handler: PartialFunction[WSResponse, Future[Result]] = PartialFunction.empty): Future[Result] = {

    val cookieHeader = if (cookies.nonEmpty) Some(COOKIE -> Cookies.encodeCookieHeader(cookies)) else None

    val contentLengthHeader = if (body.nonEmpty) Some(CONTENT_LENGTH -> body.size.toString) else None

    def handleResponse: PartialFunction[WSResponse, Future[Result]] = {
      case (response) => Future.successful {
        Status(response.status)(response.body)
          .as(response.header(CONTENT_TYPE).getOrElse(TEXT))
      }
    }

    ws.url(destination)
      .withFollowRedirects(follow = followRedirects)
      .withHeaders(headers ++ contentLengthHeader ++ cookieHeader: _*)
      .withQueryString(queryString: _*)
      .withRequestTimeout(TIMEOUT)
      .withBody(body)
      .execute(method).flatMap(handler orElse handleResponse)
  }


  def get(
           destination: String,
           headers: Seq[(String, String)] = Seq.empty,
           cookies: Seq[Cookie] = Seq.empty,
           queryString: Seq[(String, String)] = Seq.empty,
           followRedirects: Boolean = false
           )(handler: PartialFunction[WSResponse, Future[Result]] = PartialFunction.empty) =
    proxy("GET", destination, headers, cookies, queryString, followRedirects = followRedirects)(handler)


  def post(
            destination: String,
            headers: Seq[(String, String)] = Seq.empty,
            cookies: Seq[Cookie] = Seq.empty,
            queryString: Seq[(String, String)] = Seq.empty,
            body: Map[String, Seq[String]] = Map.empty,
            followRedirects: Boolean = false
            )(handler: PartialFunction[WSResponse, Future[Result]] = PartialFunction.empty) =
    proxy("POST", destination, headers, cookies, queryString, body, followRedirects)(handler)

}