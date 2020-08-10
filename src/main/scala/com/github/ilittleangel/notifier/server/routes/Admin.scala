package com.github.ilittleangel.notifier.server.routes

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes.{BadRequest, OK}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RemoteAddress}
import akka.http.scaladsl.server.{Directives, Route}
import com.github.ilittleangel.notifier.server.JsonSupport
import com.github.ilittleangel.notifier.utils.{FixedList, FixedListFactory}
import com.github.ilittleangel.notifier.{AlertPerformed, ErrorResponse, SuccessResponse, remoteAddressInfo}


trait Admin extends Directives with JsonSupport {

  implicit val log: LoggingAdapter
  implicit def system: ActorSystem
  implicit var alerts: FixedList[AlertPerformed]
  implicit var ip: RemoteAddress

  val adminEndpoint = "admin"

  val adminRoutes: Route = pathPrefix(adminEndpoint) {
    path("alerts" / "capacity") {
      post { parameter("capacity".as[Int]) { capacity => setCapacity(capacity) }} ~
      get { getCapacity }
    } ~
    path("logging") {
      post { parameter("level".as[String]) { level => setLogLevel(level) }} ~
      get { getLogLevel }
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
    complete(OK, SuccessResponse(OK, s"Request of change in-memory alerts list capacity to $capacity", remoteAddressInfo(ip)))
  }

  /**
   * Inform with the current DimensionalSeq capacity.
   *
   * @return a Route to response.
   */
  private def getCapacity: Route = {
    complete(OK, HttpEntity(ContentTypes.`application/json`, s"""{ "capacity": ${alerts.capacity} }"""))
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
        complete(OK, SuccessResponse(OK, clientIp = remoteAddressInfo(ip),
          reason = s"Request of change logging level to '${level.toUpperCase}'"))

      case None =>
        log.error("setLogLevel({}) failed", level)
        complete(BadRequest, ErrorResponse(BadRequest, clientIp = remoteAddressInfo(ip),
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
