package com.github.ilittleangel.notifier.config

import com.github.ilittleangel.notifier._
import com.typesafe.config.Config

case class FtpConfig(user: String,
                     pass: String,
                     host: Option[String],
                     port: Option[String],
                     path: Option[String],
                     protocol: Option[String],
                     withPrivateKey: Boolean)

object FtpConfig {

  def apply(config: Config): FtpConfig = {
    FtpConfig(
      user = config.getString("ftp.username"),
      pass = config.getString("ftp.password"),
      host = config.getStringOption("ftp.host"),
      port = config.getStringOption("ftp.port"),
      path = config.getStringOption("ftp.path"),
      protocol = config.getStringOption("ftp.protocol"),
      withPrivateKey = config.getBooleanOption("ftp.with_private_key").getOrElse(false),
    )
  }

}
