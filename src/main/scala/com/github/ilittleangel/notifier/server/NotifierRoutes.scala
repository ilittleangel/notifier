package com.github.ilittleangel.notifier.server

import java.time.Instant

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import com.github.ilittleangel.notifier.destinations.{Destination, Email, Slack, Ftp}
import com.github.ilittleangel.notifier.{ActionPerformed, Alert, ErrorResponse}

import scala.concurrent.Future
import scala.concurrent.duration._


trait NotifierRoutes extends JsonSupport with Directives {

  // these abstract will be provided by the NotifierServer
  implicit def system: ActorSystem

  lazy val log: LoggingAdapter = Logging(system, classOf[NotifierRoutes])

  implicit lazy val timeout: Timeout = Timeout(5.seconds)

  implicit def defaultTs: Instant = Instant.now()

  var alerts = List.empty[ActionPerformed]

  val basePath = "notifier/api/v1"
  val alertsEndpoint = "alerts"

  lazy val notifierRoutes: Route =
    pathPrefix(separateOnSlashes(basePath)) {
      pathPrefix(alertsEndpoint) {
        pathEnd {
          extractMatchedPath { path =>
            post {
              entity(as[Alert]) { alert =>
                log.info("POST '{}' with {}", path, alert)

                alert match {
                  case Alert(Email, message, props, ts) =>
                    complete(BadRequest, ErrorResponse(BadRequest.intValue, BadRequest.reason, "Email destination not implemented yet!"))

                  case Alert(destination, message, Some(props), _) =>
                    val future = destination.send(message, props)
                    evalFutureResponse(future, alert)

                  case Alert(destination, _, None, _) =>
                    missedPropertiesResponse(destination)
                }
              }
            } ~
              get {
                log.info("GET '{}'", path)
                complete(OK, alerts)
              }
          }
        } // todo: añadir un query string para el RESET
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
  def evalFutureResponse(future: Future[Either[String, String]], alert: Alert): Route =
    onSuccess(future) {
      case Right(status) =>
        val alertPerformed = ActionPerformed(alert.checkTimestamp, isPerformed = true, status, "alert received and performed!")
        alerts = alertPerformed :: alerts
        complete(OK, alertPerformed)

      case Left(error) =>
        val alertPerformed = ActionPerformed(alert.checkTimestamp, isPerformed = false, error, "alert received but not performed!")
        alerts = alertPerformed :: alerts
        complete(BadRequest, alertPerformed)
    }

  /**
   * Inform the missed properties field in the HTTP request Json body.
   *
   * @param destination to Slack, Ftp, Email..
   * @return a Route to response.
   */
  def missedPropertiesResponse(destination: Destination): Route = {
    log.error(s"$destination alert request with no properties")
    destination match {
      case Slack =>
        complete(BadRequest, ErrorResponse(BadRequest.intValue, BadRequest.reason,
          "Slack alert with no properties", Some("Include properties with 'webhook' url")))

      case Ftp =>
        complete(BadRequest, ErrorResponse(BadRequest.intValue, BadRequest.reason,
          "Ftp alert with no properties", Some("Include properties with 'host' and 'path'")))

      case Email =>
        complete(BadRequest, ErrorResponse(BadRequest.intValue, BadRequest.reason,
          "Email alert with no properties", Some("Include properties with 'server', 'port' and 'subject'")))
    }
  }


}
