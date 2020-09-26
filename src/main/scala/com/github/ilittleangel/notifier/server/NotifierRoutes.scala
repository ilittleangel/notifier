package com.github.ilittleangel.notifier.server

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.RemoteAddress
import akka.http.scaladsl.server.Route
import com.github.ilittleangel.notifier.AlertPerformed
import com.github.ilittleangel.notifier.utils.FixedList
import spray.json._


trait NotifierRoutes extends routes.Alerts with routes.Admin {

  // these abstract will be provided by the NotifierServer
  implicit def system: ActorSystem

  lazy val log: LoggingAdapter = Logging(system, classOf[NotifierRoutes])

  var alerts: FixedList[AlertPerformed] = new FixedList[AlertPerformed](capacity = 100).empty
  var ip: RemoteAddress = _

  val basePath = "notifier/api/v1"

  lazy val notifierRoutes: Route =
    pathPrefix(separateOnSlashes(basePath)) {
      extractClientIP { clientIp =>
        extractRequest { request =>
          entity(as[String]) { entity =>
            val body = if (!entity.isEmpty) entity.parseJson.compactPrint
            log.info(s"${request.method.value} ${request.uri.toRelative} $body")
            ip = clientIp
            alertsRoutes ~ adminRoutes
          }
        }
      }
    }

}