package com.github.ilittleangel

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}

import com.github.ilittleangel.notifier.destinations.Destination
import com.typesafe.config.Config

import scala.util.Try


package object notifier {

  final case class Alert(destination: Destination, message: String, properties: Option[Map[String, String]], ts: Option[Instant])
  final case class ActionPerformed(alert: Alert, isPerformed: Boolean, status: String, description: String)
  final case class Alerts(alerts: List[ActionPerformed])
  final case class ErrorResponse(status: Int, statusText: String, reason: String, possibleSolution: Option[String] = None)

  implicit class AlertOps(alert: Alert) {
    def checkTimestamp(implicit ts: Instant): Alert = alert match {
      case Alert(_, _, _, Some(_)) => alert
      case Alert(_, _, _, None)    => alert.copy(ts = Some(ts))
    }
  }

  val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)

  implicit class NotifierConfigOps(self: Config) {
    def toOption(configPath: String): Option[String] = {
      Try(Some(self.getString(configPath))).getOrElse(None)
    }
  }

}

