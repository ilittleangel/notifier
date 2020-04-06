package com.github.ilittleangel.notifier.destinations

import java.io.IOException

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{MediaTypes, StatusCodes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContextExecutor, Future}


object SlackClient extends DestinationClient {

  implicit val system: ActorSystem = ActorSystem("SlackClient")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  override def send(webHook: String, message: String): Future[Either[String, String]] = {
    val body =
      s"""
        |{
        |   "text": "$message"
        |}
        |""".stripMargin
    val httpRequest = Post(uri = webHook, body).addHeader(Accept(MediaTypes.`application/json`))

    Http().singleRequest(httpRequest).flatMap { response =>
      response.status match {
        case StatusCodes.OK => Unmarshal(response.entity).to[String].map(Right(_))
        case _ => Unmarshal(response.entity).to[String].flatMap { entity =>
          val error = s"Slack webhook request failed [status=${response.status}] [entity=$entity]"
          Future.failed(new IOException(error))
        }
      }
    }
  }
}
