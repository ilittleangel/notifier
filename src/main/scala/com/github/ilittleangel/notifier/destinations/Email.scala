package com.github.ilittleangel.notifier.destinations

import com.github.ilittleangel.notifier.config.{EmailConfig, HtmlButton}
import com.github.ilittleangel.notifier.destinations.clients.EmailClient
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.util.Try


case object Email extends Destination with EmailClient {

  override var emailConfig: EmailConfig = _

  override def configure(config: Config): Unit = {
    emailConfig = EmailConfig.apply(config)
  }

  override def send(message: String, props: Map[String, String]): Future[Either[String, String]] = {
    try {
      val subject = props("subject")
      val to = props("email_to")
      val cc = props.get("email_cc")
      val template = props.get("template")
      val htmlButton = HtmlButton(
        enable = Try(props("button_enable").toBoolean).getOrElse(emailConfig.htmlButton.enable),
        name = props.get("button_name").orElse(emailConfig.htmlButton.name),
        link = props.get("button_link").orElse(emailConfig.htmlButton.link)
      )

      Future.successful {
        sendSmtpEmail(message, subject, to, cc, template, htmlButton)
        Right("Email alert success")
      }
    } catch {
      case e: Exception =>
        Future.successful(Left(s"Email alert failed with an Exception [error=$e]"))
    }
  }
}
