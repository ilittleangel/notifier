package com.github.ilittleangel.notifier.destinations

import akka.http.scaladsl.model.HttpMethods.POST
import com.github.ilittleangel.notifier.destinations.clients.HttpClient
import com.github.ilittleangel.notifier.server.JsonSupport
import com.typesafe.config.Config
import spray.json._

import scala.concurrent.Future


case object Slack extends Destination with HttpClient with JsonSupport {

  override def configure(config: Config): Unit = {}

  override def send(message: String, props: Map[String, String]): Future[Either[String, String]] = {
    try {
      val proxy = props.get("proxy")
      val url = props("webhook")
      performHttpRequest(POST, url, payload = Some(Payload(message).toJson), proxy, "Slack alert")

    } catch {
      case e: Exception =>
        Future.successful(Left(s"Slack alert failed with an Exception [error=${e.getMessage}]"))
    }
  }

  case class Payload(text: String)

}
