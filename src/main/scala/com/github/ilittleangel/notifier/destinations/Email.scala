package com.github.ilittleangel.notifier.destinations

import com.typesafe.config.Config

import scala.concurrent.Future


case object Email extends Destination {

  override def send(message: String, props: Map[String, String]): Future[Either[String, String]] = ???

  override def configure(config: Config): Unit = {}
}
