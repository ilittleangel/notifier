package com.github.ilittleangel.notifier.server

import java.time.Instant

import akka.actor.{Actor, ActorLogging, Props}
import com.github.ilittleangel.notifier._


object NotifierActor {
  final case class ActionPerformed(description: String, destination: Destination, message: String, ts: Instant)
  final case class SendAlert(alert: Alert)
  final case object PeekAlerts
  final case object GetAndResetAlerts

  def props: Props = Props[NotifierActor]

  implicit val defaultTs: Instant = Instant.now()
}

class NotifierActor extends Actor with ActorLogging {
  import NotifierActor._

  var alerts = List.empty[Alert]

  override def receive: Receive = {

    case SendAlert(alert) =>
      alerts = alert.checkTs :: alerts
      alert match {
        case Alert(Tivoli, message, ts) =>
          // Todo: implement Tivoli alert
          feedback(alert)

        case Alert(Slack, message, ts) =>
          // Todo: implement Slack alert request
          feedback(alert)

        case Alert(Email, message, ts) =>
          // Todo: implement email alert request
          feedback(alert)
      }

    case PeekAlerts =>
      sender() ! Alerts(alerts)

    case GetAndResetAlerts =>
      sender() ! Alerts(alerts)
      alerts = Nil

  }

  /**
   * Inform with ActionPerformed in the HTTP response Json body.
   *
   * @param alert received in the HTTP request.
   */
  def feedback(alert: Alert)(implicit ts: Instant): Unit = alert match {
    case Alert(destination, message, Some(ts)) =>
      sender() ! ActionPerformed(s"$destination alert sent!", destination, message, ts)

    case Alert(destination, message, None) =>
      sender() ! ActionPerformed(s"$destination alert with no 'ts' sent! Instant.now will be use.", destination, message, ts)
  }

}
