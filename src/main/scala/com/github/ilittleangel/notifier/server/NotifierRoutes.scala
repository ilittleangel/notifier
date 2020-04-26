package com.github.ilittleangel.notifier.server

import java.net.InetAddress
import java.time.Instant

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import com.github.ilittleangel.notifier.destinations.{Destination, Email, Ftp, Slack}
import com.github.ilittleangel.notifier.{ActionPerformed, Alert, ErrorResponse, _}

import scala.concurrent.Future
import scala.concurrent.duration._


trait NotifierRoutes extends JsonSupport with Directives {

  // these abstract will be provided by the NotifierServer
  implicit def system: ActorSystem

  lazy val log: LoggingAdapter = Logging(system, classOf[NotifierRoutes])

  implicit lazy val timeout: Timeout = Timeout(10.seconds)

  def defaultTs: Instant = Instant.now()

  var alerts = List.empty[ActionPerformed]

  val basePath = "notifier/api/v1"
  val alertsEndpoint = "alerts"

  lazy val notifierRoutes: Route =
    pathPrefix(separateOnSlashes(basePath)) {
      extractClientIP { ip =>
        pathPrefix(alertsEndpoint) {
          pathEnd {
            extractMatchedPath { path =>
              post {
                entity(as[Alert]) { alert =>
                  log.info("POST '{}' with {}", path, alert)

                  alert match {
                    case Alert(Email, _, _, _) =>
                      complete(BadRequest, ErrorResponse(BadRequest.intValue, BadRequest.reason,
                        "Email destination not implemented yet!", clientIp = showOriginIpInfo(ip.toOption)))

                    case Alert(destination, message, Some(props), _) =>
                      val future = destination.send(message, props)
                      evalFutureResponse(future, alert, ip.toOption)

                    case Alert(destination, _, None, _) =>
                      missedPropertiesResponse(destination, ip.toOption)
                  }

                }
              } ~
              get {
                log.info("GET '{}'", path)
                complete(OK, alerts)
              } ~
              delete {
                log.info("DELETE '{}'", path)
                alerts = Nil
                complete(OK, alerts)
              }
            }
          }
        }
      }
    }

  /**
   * Inform with ActionPerformed in the HTTP response Json body
   * and increase the mutable list of alerts.
   *
   * @param future with Alert information that will be performed or not.
   * @param alert  the alert itself.
   * @return a Route for response.
   */
  def evalFutureResponse(future: Future[Either[String, String]], alert: Alert, ip: Option[InetAddress]): Route = {
    val response = ActionPerformed(alert.ensureTimestamp(defaultTs), isPerformed = false, "", "", showOriginIpInfo(ip))

    onSuccess(future) {
      case Right(status) =>
        val alertPerformed = response.copy(isPerformed = true, status = status, description = "alert received and performed!")
        alerts = alertPerformed :: alerts
        complete(OK, alertPerformed)

      case Left(error) =>
        val alertPerformed = response.copy(isPerformed = false, status = error, description = "alert received but not performed!")
        alerts = alertPerformed :: alerts
        complete(BadRequest, alertPerformed)
    }
  }

  /**
   * Inform the missed properties field in the HTTP request Json body.
   *
   * @param destination to Slack, Ftp, Email..
   * @param ip from remote client.
   * @return a Route to response.
   */
  def missedPropertiesResponse(destination: Destination, ip: Option[InetAddress]): Route = {
    log.error(s"$destination alert request with no properties")
    val response = ErrorResponse(BadRequest.intValue, BadRequest.reason, "", None, showOriginIpInfo(ip))

    destination match {
      case Slack =>
        complete(BadRequest, response.copy(
          reason = "Slack alert with no properties",
          possibleSolution = Some("Include properties with 'webhook' url"))
        )

      case Ftp =>
        complete(BadRequest, response.copy(
          reason = "Ftp alert with no properties",
          possibleSolution = Some("Include properties with 'host' and 'path'"))
        )

      case Email =>
        complete(BadRequest, response.copy(
          reason = "Email alert with no properties",
          possibleSolution = Some("Include properties with 'server', 'port' and 'subject'"))
        )
    }
  }


  /**
   * Show the client's IP if the specific config attribute `show_origin_ip` is true.
   *
   * @param ip extracted from either the X-Forwarded-For, Remote-Address or X-Real-IP HTTP header.
   * @return a RemoteClientIp object if the properly config attribute is true.
   */
  def showOriginIpInfo(ip: Option[InetAddress]): Option[String] = {
    val hostname = applyOrElse(ip)(_.getHostName, "unknown")
    val hostAddress = applyOrElse(ip)(_.getHostAddress, "unknown")
    val ipInfo = s"$hostname - $hostAddress"
    log.debug(s"HTTP request performed from: $ipInfo")

    ip match {
      case Some(_) => Some(ipInfo)
      case None => None
    }
  }

}