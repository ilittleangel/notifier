package com.github.ilittleangel.notifier.server

import java.time.Instant

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.github.ilittleangel.notifier._
import com.github.ilittleangel.notifier.destinations.{Destination, Email, Ftp, Slack}
import spray.json.{DefaultJsonProtocol, DeserializationException, JsArray, JsString, JsValue, JsonFormat, RootJsonFormat}

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
  implicit object DestinationFormat extends JsonFormat[List[Destination]] {

    override def read(json: JsValue): List[Destination] = json match {
      case JsArray(elements) => elements.toList.map(_.toDestination)
      case JsString(str) => List(str.toDestination)
      case _ => throw DeserializationException(DeserializationError)
    }

    override def write(obj: List[Destination]): JsValue =
      JsArray(obj.map(dest => dest.toJson).toVector)
  }

  implicit val alertJsonBody: RootJsonFormat[Alert] = jsonFormat4(Alert)
  implicit val alertsJsonBody: RootJsonFormat[Alerts] = jsonFormat1(Alerts)
  implicit val actionPerformedJsonFormat: RootJsonFormat[ActionPerformed] = jsonFormat5(ActionPerformed)
  implicit val errorResponseJsonFormat: RootJsonFormat[ErrorResponse] = jsonFormat5(ErrorResponse)
  implicit val successResponseJsonFormat: RootJsonFormat[SuccessResponse] = jsonFormat4(SuccessResponse)

  implicit private class StrOps(str: String) {
    def toDestination: Destination = str.toLowerCase match {
        case "ftp" => Ftp
        case "slack" => Slack
        case "email" => Email
        case _ => throw DeserializationException(DeserializationError)
      }
  }

  implicit private class JsonOps(json: JsValue) {
    def toDestination: Destination = json match {
        case JsString(str) => str.toDestination
        case _ => throw DeserializationException(DeserializationError)
    }
  }

  implicit private class DestOps(dest: Destination) {
    def toJson: JsValue = dest match {
      case Ftp => JsString("ftp")
      case Slack => JsString("slack")
      case Email => JsString("email")
      case _ => throw DeserializationException(DeserializationError)
    }
  }
}
