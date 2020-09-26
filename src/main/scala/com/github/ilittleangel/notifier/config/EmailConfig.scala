package com.github.ilittleangel.notifier.config

import com.typesafe.config.Config
import com.github.ilittleangel.notifier._

case class HtmlButton(enable: Boolean,
                      name: Option[String],
                      link: Option[String])

case class EmailConfig(server: String,
                       port: Int,
                       user: String,
                       pass: Option[String],
                       ttls: Boolean,
                       htmlButton: HtmlButton)

object EmailConfig {
  def apply(config: Config): EmailConfig = {
    EmailConfig(
      server = config.getString("email.server"),
      port = config.getInt("email.port"),
      user = config.getString("email.user"),
      pass = config.getStringOption("email.pass"),
      ttls = config.getBoolean("email.ttls"),
      htmlButton = HtmlButton(
        enable = config.getBoolean("email.html.button.enable"),
        name = config.getStringOption("email.html.button.name"),
        link = config.getStringOption("email.html.button.link")
      )
    )
  }
}

