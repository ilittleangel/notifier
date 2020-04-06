package com.github.ilittleangel.notifier.server

import akka.actor.{ActorRef, ActorSystem, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success}


object NotifierServer extends NotifierRoutes {

  implicit val system: ActorSystem = ActorSystem("NotifierServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher

  val notifierActor: ActorRef = system.actorOf(NotifierActor.props, "NotifierActor")
  lazy val routes: Route = notifierRoutes

  def start(host: String, port: Int): Unit = {
    val serverBinding = Http().bindAndHandle(routes, host, port)

    serverBinding.onComplete {
      case Success(bound) =>
        log.info(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
      case Failure(exception) =>
        log.error(s"Server could not start!")
        exception.printStackTrace()
        system.terminate()
    }
  }

  def stop(): Terminated = {
    log.info(s"Shutdown Push Gateway Server ..")
    Await.result(system.whenTerminated, Timeout(30.seconds).duration)
  }
}
