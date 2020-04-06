package com.github.ilittleangel.notifier.destinations

import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.Config

import scala.concurrent.Future


case object Slack extends Destination {

  override def configure(config: Config): Unit = {}

  override def send(message: String, props: Map[String, String]): Future[Either[String, String]] = {
    val body =
      s"""
         |{
         |   "text": "$message"
         |}
         |""".stripMargin

    try {
      val httpRequest = Post(uri = props("webhook"), body).addHeader(Accept(MediaTypes.`application/json`))
      Http().singleRequest(httpRequest).flatMap { response =>
        response.status match {
          case OK =>
            val msg = s"Slack webhook request success [status=${response.status}]"
            log.info(msg)
            Unmarshal(response.entity).to[String].map(res => Right(s"$msg [$res]"))

          case BadRequest | Forbidden | NotFound | Gone =>
            val msg = s"Slack webhook request failed [status=${response.status}]"
            log.error(msg)
            Unmarshal(response.entity).to[String].map(res => Left(s"$msg [$res]"))

          case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
            val msg = s"Slack webhook request failed with unknown error [status=${response.status}]"
            log.error(msg)
            Future.successful(Left(s"$msg [$entity]"))
          }
        }
      }
    } catch {
      case e: Exception =>
        val msg = s"Slack alert failed with an Exception [error=${e.getMessage}]"
        log.error(msg)
        Future.successful(Left(msg))
    }
  }

}
