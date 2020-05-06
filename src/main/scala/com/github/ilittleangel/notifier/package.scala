package com.github.ilittleangel

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}

import com.github.ilittleangel.notifier.destinations.Destination
import com.typesafe.config.Config

import scala.util.Try


package object notifier {

  final case class Alert(destination: List[Destination], message: String, properties: Option[Map[String, String]], ts: Option[Instant])
  final case class ActionPerformed(alert: Alert, isPerformed: Boolean, status: String, description: String, clientIp: Option[String])
  final case class Alerts(alerts: List[ActionPerformed])
  final case class ErrorResponse(status: Int, statusText: String, reason: String, possibleSolution: Option[String] = None, clientIp: Option[String])

  implicit class AlertOps(alert: Alert) {
    def ensureTimestamp(default: Instant): Alert = alert match {
      case Alert(_, _, _, Some(_)) => alert
      case Alert(_, _, _, None)    => alert.copy(ts = Some(default))
    }
  }

  val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)

  implicit class NotifierConfigOps(self: Config) {
    def getStringOption(configPath: String): Option[String] =
      Try(self.getString(configPath)).toOption

    def getBooleanOption(configPath: String): Option[Boolean] =
      Try(self.getBoolean(configPath)).toOption
  }

  def applyOrElse[A, B](optValue: Option[A])(f: A => B, default: B): B = optValue.map(f(_)).getOrElse(default)

  /**
   * Global constants
   */
  val DeserializationError = "Expected (Ftp, Slack or Email) for 'destination' attribute"
  val AlertPerformed = "alert received and performed!"
  val AlertNotPerformed = "alert received but not performed!"


}

