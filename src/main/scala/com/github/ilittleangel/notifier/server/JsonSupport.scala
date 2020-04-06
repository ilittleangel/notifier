package com.github.ilittleangel.notifier.server

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.github.ilittleangel.notifier.server.NotifierActor.ActionPerformed
import com.github.ilittleangel.notifier.{Alert, Alerts, Destination, Email, Slack, Tivoli}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, RootJsonFormat}

import scala.util.Try


trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  /**
   * Json to Instant serialization.
   */
  implicit object DateJsonFormat extends JsonFormat[Instant] {
    val formatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC)

    override def read(json: JsValue): Instant = json match {
      case JsString(str) => Try(Instant.from(formatter.parse(str))).getOrElse(
        throw new RuntimeException("Invalid datetime format: " + str))
      case _ => throw DeserializationException("Expected string type for datetime attribute: " + json)
    }

    override def write(obj: Instant): JsValue = JsString(formatter.format(obj))
  }

  /**
   * Serde for Json to Destination type.
   */
  implicit object AlertTypeFormat extends JsonFormat[Destination] {
    val errorMsg = "Expected (Tivoli, Slack or Email) for 'destination' attribute"

    override def read(json: JsValue): Destination = json match {
      case JsString(str) => str.toLowerCase match {
        case "tivoli" => Tivoli
        case "slack" => Slack
        case "email" => Email
        case _ => throw DeserializationException(errorMsg)
      }
      case _ => throw DeserializationException(errorMsg)
    }

    override def write(obj: Destination): JsValue = obj match {
      case Tivoli => JsString("tivoli")
      case Slack => JsString("slack")
      case Email => JsString("email")
    }
  }

  implicit val alertJsonBody: RootJsonFormat[Alert] = jsonFormat3(Alert)
  implicit val alertsJsonBody: RootJsonFormat[Alerts] = jsonFormat1(Alerts)
  implicit val actionPerformedJsonFormat: RootJsonFormat[ActionPerformed] = jsonFormat4(ActionPerformed)
}
