package com.github.ilittleangel.notifier.server

import java.time.Instant

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import com.github.ilittleangel.notifier.destinations.SlackClient
import com.github.ilittleangel.notifier.{ActionPerformed, Alert, Slack}

import scala.concurrent.duration._


trait NotifierRoutes extends JsonSupport with Directives {

  // these abstract will be provided by the NotifierServer
  implicit def system: ActorSystem

  lazy val log: LoggingAdapter = Logging(system, classOf[NotifierRoutes])

  implicit lazy val timeout: Timeout = Timeout(5.seconds)

  implicit def defaultTs: Instant = Instant.now()

  var alerts = List.empty[ActionPerformed]

  lazy val notifierRoutes: Route =
    pathPrefix("notifier" / "api" / "v1") {
      pathPrefix("alert") {
        pathEnd {
          extractMatchedPath { path =>
            post {
              entity(as[Alert]) {

                case alert @ Alert(Slack, message, Some(props), _) =>
                  val future = SlackClient.send(props("webhook"), message)
                  onSuccess(future) {
                    case Right(status) =>
                      log.info("POST '{}' with {}", path, alert)
                      alerts = feedback(alert.checkTimestamp, isPerformed = true, status) :: alerts
                      complete(StatusCodes.OK, feedback(alert, isPerformed = true, status))
                    case Left(error) =>
                      log.error("POST '{}' with {}", path, alert)
                      alerts = feedback(alert.checkTimestamp, isPerformed = false, error) :: alerts
                      complete(StatusCodes.BadRequest, feedback(alert, isPerformed = false, error))
                  }

                /*
              case Alert(Tivoli, message, props, ts) =>
              // Todo: implement Tivoli alert

              case Alert(Slack, _, None, _) =>
                // Todo: use handleExceptions or failWith when no properties

              case Alert(Email, message, props, ts) =>
                // Todo: implement email alert
                */
              }
            } ~
              get {
                log.info("GET '{}'", path)
                complete(StatusCodes.OK, alerts)
              }
          }
        } // todo: añadir un query string para el RESET
      }
    }


  /**
   * Inform with ActionPerformed in the HTTP response Json body.
   *
   * @param alert       received in the HTTP request.
   * @param isPerformed the action triggered because of alert.
   * @param status      of the action.
   */
  def feedback(alert: Alert, isPerformed: Boolean = false, status: String = ""): ActionPerformed = {
    val desc = if (isPerformed) "alert received and performed!" else "alert received but not performed!"
    alert match {
      case Alert(_, _, _, Some(_)) =>
        ActionPerformed(alert, isPerformed, status, desc)

      case Alert(_, _, _, None) =>
        ActionPerformed(alert, isPerformed, status, s"$desc None 'timestamp': Instant.now() will be use.")
    }
  }


}