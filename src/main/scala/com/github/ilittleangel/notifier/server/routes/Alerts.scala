package com.github.ilittleangel.notifier.server.routes

import java.time.Instant

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.RemoteAddress
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK}
import akka.http.scaladsl.server.{Directives, Route}
import com.github.ilittleangel.notifier.Constants.{AlertNotPerformedMsg, AlertPerformedMsg}
import com.github.ilittleangel.notifier.destinations.Destination
import com.github.ilittleangel.notifier.server.JsonSupport
import com.github.ilittleangel.notifier.utils.{Eithers, FixedList}
import com.github.ilittleangel.notifier.{Alert, AlertPerformed, ErrorResponse, remoteAddressInfo}


trait Alerts extends Directives with JsonSupport with Eithers {

  implicit val log: LoggingAdapter
  implicit def system: ActorSystem
  implicit var alerts: FixedList[AlertPerformed]
  implicit var ip: RemoteAddress

  def defaultTs: Instant = Instant.now()

  val alertsEndpoint = "alerts"

  val alertsRoutes: Route = pathPrefix(alertsEndpoint) {
    pathEnd {
      post {
        entity(as[Alert]) {
          case alert @ Alert(_, _, Some(_), Some(_)) =>
            processAlert(alert)

          case alert @ Alert(_, _, Some(_), None) =>
            processAlert(alert.copy(ts = Some(defaultTs)))

          case Alert(destinations, _, None, _) =>
            missedPropertiesResponse(destinations)
        }
      } ~
      get { complete(OK, alerts.toList.reverse) } ~
      delete { alerts = alerts.empty; complete(OK, alerts.toList) }
    }
  }

  /**
   * Inform with ActionPerformed in the HTTP response Json body
   * and increase the mutable list of alerts.
   *
   * @param alert  the alert itself.
   * @return a Route for response.
   */
  def processAlert(alert: Alert): Route = {
    val future = alert.destination.map(_.send(alert.message, alert.properties.get)).reduceEithers()
    onSuccess(future) {
      case Right(status) =>
        complete(OK, generateAlertResponse(alert, isPerformed = true, status))

      case Left(error) =>
        complete(BadRequest, generateAlertResponse(alert, isPerformed = false, error))
    }
  }

  def generateAlertResponse(alert: Alert, isPerformed: Boolean, status: String): AlertPerformed = {
    val description = if (isPerformed) { log.info(status); AlertPerformedMsg } else { log.error(status); AlertNotPerformedMsg }
    val alertPerformed = AlertPerformed(alert, isPerformed, status, description, remoteAddressInfo(ip))
    alerts = alerts :+ alertPerformed
    alertPerformed
  }

  /**
   * Inform the missed properties field in the HTTP request Json body.
   *
   * @param destinations to Slack, Ftp, Email..
   * @return a Route to response.
   */
  def missedPropertiesResponse(destinations: List[Destination]): Route = {
    log.error("{} alert request with no properties", destinations)
    val response = ErrorResponse(
      status = BadRequest,
      reason = s"Some of these destinations $destinations has no properties",
      possibleSolution = Some(s"Include required properties. See documentation"),
      clientIp = remoteAddressInfo(ip)
    )
    complete(BadRequest, response)
  }

}
