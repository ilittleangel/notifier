package com.github.ilittleangel.notifier.server

import java.time.Instant

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RemoteAddress}
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK}
import akka.http.scaladsl.server.{Directives, Route}
import com.github.ilittleangel.notifier.destinations.{Destination, Email, Ftp, Slack}
import com.github.ilittleangel.notifier.utils.{Eithers, FixedList, FixedListFactory}
import com.github.ilittleangel.notifier.{ActionPerformed, Alert, ErrorResponse, _}

import scala.concurrent.Future


trait NotifierRoutes extends JsonSupport with Directives with Eithers {

  // these abstract will be provided by the NotifierServer
  implicit def system: ActorSystem

  lazy val log: LoggingAdapter = Logging(system, classOf[NotifierRoutes])

  def defaultTs: Instant = Instant.now()

  var alerts: FixedList[ActionPerformed] = new FixedList[ActionPerformed](capacity = 100).empty
  var ip: RemoteAddress = _

  val basePath = "notifier/api/v1"
  val alertsEndpoint = "alerts"
  val adminEndpoint = "admin"

  lazy val notifierRoutes: Route =
    pathPrefix(separateOnSlashes(basePath)) {
      extractClientIP { clientIp =>
        ip = clientIp
        pathPrefix(alertsEndpoint) {
          pathEnd {
            extractMatchedPath { path =>
              post {
                entity(as[Alert]) { alert =>
                  log.info("POST '{}' with {}", path, alert)

                  alert match {
                    case Alert(destinations, message, Some(props), _) =>
                      val future = destinations.map(_.send(message, props)).reduceEithers()
                      evalFutureResponse(future, alert)

                    case Alert(destinations, _, None, _) =>
                      missedPropertiesResponse(destinations)
                  }

                }
              } ~
              get {
                log.info("GET '{}'", path)
                complete(OK, alerts.toList.reverse)
              } ~
              delete {
                log.info("DELETE '{}'", path)
                alerts = alerts.empty
                complete(OK, alerts.toList)
              }
            }
          }
        } ~
        pathPrefix(adminEndpoint) {
          path("set-alerts-capacity") {
            extractMatchedPath { path =>
              post {
                parameter("capacity".as[Int]) { capacity =>
                  log.info("POST '{}' with capacity = {}", path, capacity)
                  setCapacity(capacity)
                }
              }
            }
          } ~
          path("logging") {
            extractMatchedPath { path =>
              post {
                parameter("level".as[String]) { level =>
                  log.info("POST '{}' with logLevel = {}", path, level)
                  setLogLevel(level)
                }
              } ~
              get {
                log.info("GET '{}'", path)
                getLogLevel
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
  def evalFutureResponse(future: Future[Either[String, String]], alert: Alert): Route = {
    val response = ActionPerformed(alert.ensureTimestamp(defaultTs), isPerformed = false, "", "", remoteAddressInfo(ip))

    onSuccess(future) {
      case Right(status) =>
        val alertPerformed = response.copy(isPerformed = true, status = status, description = AlertPerformed)
        alerts = alerts :+ alertPerformed
        complete(OK, alertPerformed)

      case Left(error) =>
        val alertPerformed = response.copy(isPerformed = false, status = error, description = AlertNotPerformed)
        alerts = alerts :+ alertPerformed
        complete(BadRequest, alertPerformed)
    }
  }

  /**
   * Inform the missed properties field in the HTTP request Json body.
   *
   * @param destinations to Slack, Ftp, Email..
   * @return a Route to response.
   */
  def missedPropertiesResponse(destinations: List[Destination]): Route = {
    log.error(s"{} alert request with no properties", destinations)
    val response = ErrorResponse(BadRequest.intValue, BadRequest.reason, "", None, remoteAddressInfo(ip))

    destinations.head match {
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
   * Set the FixedList capacity.
   *
   * @param capacity of the FixedList.
   * @return a Route to response.
   */
  def setCapacity(capacity: Int): Route = {
    object FixedList extends FixedListFactory(capacity)
    alerts = alerts.to(FixedList)
    complete(OK, SuccessResponse(OK.intValue, OK.reason,
      s"Request of change in-memory alerts list capacity to $capacity",
      remoteAddressInfo(ip))
    )
  }

  /**
   * Online set up the logging level through `system.eventStream`.
   *
   * @param level string representation of the logging level.
   * @return a Route to response.
   */
  def setLogLevel(level: String): Route = {
    Logging.levelFor(level) match {
      case Some(loglevel) =>
        system.eventStream.setLogLevel(loglevel)
        log.info("setLogLevel({}) succeed", level)
        complete(OK, SuccessResponse(OK.intValue, OK.reason, clientIp = remoteAddressInfo(ip),
          reason = s"Request of change logging level to '${level.toUpperCase}'"))

      case None =>
        log.error("setLogLevel({}) failed", level)
        complete(BadRequest, ErrorResponse(BadRequest.intValue, BadRequest.reason, clientIp = remoteAddressInfo(ip),
          reason = s"Request of change logging level to '$level'",
          possibleSolution = Some("Logging level must be one of [off|error|info|debug|warning]"))
        )
    }
  }

  /**
   * Inform with the current Logging Level.
   * The `pattern matching` statement has to be in descending order.
   *
   * @return a Route to response.
   */
  def getLogLevel: Route = {
    val level = system.eventStream.logLevel match {
      case Logging.DebugLevel => "DEBUG"
      case Logging.InfoLevel => "INFO"
      case Logging.WarningLevel => "WARNING"
      case Logging.ErrorLevel => "ERROR"
      case _ => "OFF"
    }
    complete(OK, HttpEntity(ContentTypes.`application/json`, s"""{ "level": "$level" }"""))
  }

}