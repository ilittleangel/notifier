package com.github.ilittleangel

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}

import akka.http.scaladsl.model.{RemoteAddress, StatusCode}
import com.github.ilittleangel.notifier.destinations.Destination
import com.typesafe.config.Config

import scala.util.Try


package object notifier {

  final case class Alert(destination: List[Destination], message: String, properties: Option[Map[String, String]], ts: Option[Instant])
  final case class AlertPerformed(alert: Alert, isPerformed: Boolean, status: String, description: String, clientIp: Option[String])
  final case class ErrorResponse(status: StatusCode, reason: String, possibleSolution: Option[String] = None, clientIp: Option[String])
  final case class SuccessResponse(status: StatusCode, reason: String, clientIp: Option[String])

  val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC)

  implicit class NotifierConfigOps(self: Config) {
    def getStringOption(configPath: String): Option[String] =
      Try(self.getString(configPath)).toOption

    def getBooleanOption(configPath: String): Option[Boolean] =
      Try(self.getBoolean(configPath)).toOption
  }

  def applyOrElse[A, B](optValue: Option[A])(f: A => B, default: B): B = optValue.map(f(_)).getOrElse(default)

  val remoteAddressInfo: RemoteAddress => Option[String] = (ip: RemoteAddress) => {
    ip.toOption match {
      case ip @ Some(_) =>
        val hostname = applyOrElse(ip)(_.getHostName, "unknown")
        val hostAddress = applyOrElse(ip)(_.getHostAddress, "unknown")
        Some(s"$hostname - $hostAddress")
      case None => None
    }
  }

  /**
   * Global constants
   */
  object Constants {
    val DeserializationErrorMsg = "Expected (Ftp, Slack or Email) for 'destination' attribute"
    val AlertPerformedMsg = "alert received and performed!"
    val AlertNotPerformedMsg = "alert received but not performed!"
  }

  sealed trait HttpSecurity
  case object Insecure extends HttpSecurity
  case class Secure(jksPath: String, pass: String) extends HttpSecurity
  case object Default extends HttpSecurity
}

