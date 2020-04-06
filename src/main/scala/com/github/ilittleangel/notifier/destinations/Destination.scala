package com.github.ilittleangel.notifier.destinations

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.stream.ActorMaterializer
import com.github.ilittleangel.notifier.server.NotifierServer
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}

trait Destination {
  lazy val log: LoggingAdapter = Logging(system, classOf[Destination])

  implicit val system: ActorSystem = NotifierServer.system
  implicit val materializer: ActorMaterializer = NotifierServer.materializer
  implicit val executionContext: ExecutionContext = NotifierServer.executionContext

  def send(message: String, properties: Map[String, String]): Future[Either[String, String]]

  def configure(config: Config): Unit
}