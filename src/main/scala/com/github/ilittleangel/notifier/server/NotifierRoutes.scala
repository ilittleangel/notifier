package com.github.ilittleangel.notifier.server

import akka.actor.{ActorRef, ActorSystem}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.ask
import akka.util.Timeout
import com.github.ilittleangel.notifier.{Alert, _}
import com.github.ilittleangel.notifier.server.NotifierActor.{ActionPerformed, PeekAlerts, SendAlert}

import scala.concurrent.Future
import scala.concurrent.duration._


trait NotifierRoutes extends JsonSupport with Directives {

  // these abstract will be provided by the PushGatewayServer
  implicit def system: ActorSystem

  lazy val log: LoggingAdapter = Logging(system, classOf[NotifierRoutes])

  // other dependencies that NotifierRoutes use
  def notifierActor: ActorRef

  implicit lazy val timeout: Timeout = Timeout(5.seconds)

  lazy val notifierRoutes: Route =
    pathPrefix("notifier" / "api" / "v1") {
      pathPrefix("alert") {
        pathEnd {
          extractMatchedPath { path =>
            post {
              entity(as[Alert]) { alert =>
                val metricRegistered: Future[ActionPerformed] = (notifierActor ? SendAlert(alert)).mapTo[ActionPerformed]
                onSuccess(metricRegistered) { performed =>
                  log.info("POST '{}' with {}", path, alert)
                  complete((StatusCodes.OK, performed))
                }
              }
            } ~
            get {
              val alerts: Future[Alerts] = (notifierActor ? PeekAlerts).mapTo[Alerts]
              log.info("GET '{}'", path)
              complete((StatusCodes.OK, alerts))
            }
          }
        }
      }
    }


}
