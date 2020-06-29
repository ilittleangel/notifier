package com.github.ilittleangel.notifier

import com.icegreen.greenmail.util.{GreenMail, ServerSetupTest}

import scala.concurrent.duration.DurationInt

trait MockMail {
  val emailUser = "alert@notifier.com"
  val emailPass = "password"
  var mailServer: GreenMail = new GreenMail(ServerSetupTest.SMTP)
  val emailTimeout: Long = 5.seconds.toMillis

  mailServer.setUser(emailUser, emailPass)
}
