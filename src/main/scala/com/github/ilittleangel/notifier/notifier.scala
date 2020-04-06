package com.github.ilittleangel

import java.time.Instant

package object notifier {

  sealed trait Destination
  case object Tivoli extends Destination
  case object Slack extends Destination
  case object Email extends Destination

  final case class Alert(destination: Destination, message: String, ts: Option[Instant])
  final case class Alerts(alerts: List[Alert])

  implicit class AlertOps(alert: Alert) {
    def checkTs(implicit ts: Instant): Alert = alert match {
      case Alert(_, _, Some(_)) => alert
      case Alert(_, _, None)    => alert.copy(ts = Some(ts))
    }
  }

}

