package com.github.ilittleangel.notifier

import com.github.ilittleangel.notifier.server.NotifierServer

object App extends App {

  NotifierServer.start("localhost", 8080)

}
