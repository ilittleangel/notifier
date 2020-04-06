package com.github.ilittleangel.notifier.destinations

import scala.concurrent.Future


trait DestinationClient {

  /**
   * Method for different implementations of sending alerts.
   *
   * @param destination url/ip/jdbc/server to send the message.
   * @param message     for sending.
   * @return future with alert sent or not
   */
  def send(destination: String, message: String): Future[Either[String, String]]

}
