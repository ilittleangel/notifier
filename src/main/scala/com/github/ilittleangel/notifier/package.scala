package com.github.ilittleangel

import java.time.Instant

package object notifier {

  sealed trait Destination
  case object Tivoli extends Destination
  case object Slack extends Destination
  case object Email extends Destination

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

}
