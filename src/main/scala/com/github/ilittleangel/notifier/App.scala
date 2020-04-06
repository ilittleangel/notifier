package com.github.ilittleangel.notifier

import java.io.File

import com.github.ilittleangel.notifier.config.ServerConfig
import com.github.ilittleangel.notifier.destinations.Ftp
import com.github.ilittleangel.notifier.server.NotifierServer
import com.typesafe.config.ConfigFactory
import scopt.OptionParser


object App extends App {

  case class Args(configFile: File)

  private val argsParser = new OptionParser[Args]("Notifier") {
    opt[File]('c', "config")
      .required()
      .text("The Notifier configuration file path")
      .valueName("<path>")
      .validate(f => if (f.canRead) success else failure(s"Cannot read config file: ${f.getAbsolutePath}"))
      .action((f, args) => args.copy(configFile = f))
  }

  argsParser.parse(args, Args(configFile = null)) match {

    case Some(arguments) =>
      val path = arguments.configFile
      val config = ConfigFactory.parseFile(path)
      val serverConfig = ServerConfig.apply(config)
      Ftp.configure(config)
      NotifierServer.start(host = serverConfig.interface, port = serverConfig.port)

    case None =>
      System.exit(1)
  }

}