package com.github.ilittleangel.notifier.config

import com.typesafe.config.Config

case class ServerConfig(interface: String, port: Int)

object ServerConfig {

  def apply(config: Config): ServerConfig = {
    ServerConfig(
      interface = config.getString("server.bind_address"),
      port = config.getInt("server.bind_port")
    )
  }

}
