package com.github.ilittleangel.notifier.config

import com.github.ilittleangel.notifier._
import com.typesafe.config.Config

case class FtpConfig(user: String,
                     pass: String,
                     host: Option[String],
                     port: Option[String],
                     path: Option[String],
                     protocol: Option[String])

object FtpConfig {

  def apply(config: Config): FtpConfig = {
    FtpConfig(
      user = config.getString("ftp.username"),
      pass = config.getString("ftp.password"),
      host = config.toOption("ftp.host"),
      port = config.toOption("ftp.port"),
      path = config.toOption("ftp.path"),
      protocol = config.toOption("ftp.protocol")
    )
  }

}
