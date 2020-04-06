package com.github.ilittleangel.notifier.server

import java.time.Instant

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.github.ilittleangel.notifier._
import com.github.ilittleangel.notifier.destinations.{Destination, Email, Slack, Ftp}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, RootJsonFormat}

import scala.util.Try


trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {

  /**
   * Json to Instant serialization.
   */
  implicit object DateJsonFormat extends JsonFormat[Instant] {
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
    val errorMsg = "Expected (Ftp, Slack or Email) for 'destination' attribute"

    override def read(json: JsValue): Destination = json match {
      case JsString(str) => str.toLowerCase match {
        case "ftp" => Ftp
        case "slack" => Slack
        case "email" => Email
        case _ => throw DeserializationException(errorMsg)
      }
      case _ => throw DeserializationException(errorMsg)
    }

    override def write(obj: Destination): JsValue = obj match {
      case Ftp => JsString("ftp")
      case Slack => JsString("slack")
      case Email => JsString("email")
    }
  }

  implicit val alertJsonBody: RootJsonFormat[Alert] = jsonFormat4(Alert)
  implicit val alertsJsonBody: RootJsonFormat[Alerts] = jsonFormat1(Alerts)
  implicit val actionPerformedJsonFormat: RootJsonFormat[ActionPerformed] = jsonFormat4(ActionPerformed)
  implicit val errorResponseJsonFormat: RootJsonFormat[ErrorResponse] = jsonFormat4(ErrorResponse)
}
