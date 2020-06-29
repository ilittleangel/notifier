package com.github.ilittleangel.notifier.destinations.clients

import com.github.ilittleangel.notifier.config.{EmailConfig, HtmlButton}
import com.sun.mail.smtp.SMTPTransport
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail.{Message, Session}

import scala.io.Source

trait EmailClient {

  var emailConfig: EmailConfig

  /**
   * Smtp client to send email message with HTML content.
   *
   * @param text       email message.
   * @param subject    email subject.
   * @param to         email destinations. Can be a list of address with comma separator.
   * @param cc         email CC destination. It is optional. Can be a list of address with comma separator.
   * @param template   HTML template to apply. Default: `main.html`
   * @param htmlButton link with button and custom name. Possibility to enable/disable.
   */
  def sendSmtpEmail(text: String, subject: String, to: String, cc: Option[String],
                    template: Option[String], htmlButton: HtmlButton): Unit = {

    val properties = System.getProperties
    properties.put("mail.smtp.host", emailConfig.server)
    properties.put("mail.smtp.port", emailConfig.port)
    properties.put("mail.smtp.starttls.enable", emailConfig.ttls)
    properties.put("mail.smtp.auth", emailConfig.pass match {
      case Some(_) => true
      case None => false
    })

    val session = Session.getInstance(properties, null)
    val mimeMessage = new MimeMessage(session)

    mimeMessage.setSubject(subject)
    mimeMessage.setContent(htmlRender(subject, text, template, htmlButton), "text/html")
    mimeMessage.setFrom(new InternetAddress(emailConfig.user))
    mimeMessage.setRecipients(Message.RecipientType.TO, to)
    cc match {
      case Some(emails) => mimeMessage.addRecipients(Message.RecipientType.CC, emails)
      case None =>
    }

    val t = session.getTransport("smtp").asInstanceOf[SMTPTransport]
    emailConfig.pass match {
      case Some(pass) => t.connect(emailConfig.server, emailConfig.user, pass)
      case None => t.connect()
    }
    t.sendMessage(mimeMessage, mimeMessage.getAllRecipients)
    t.close()
  }

  /**
   * Render an HTML applying differences replacements:
   * - In the email message replace JSON to HTML newLines and whitespaces
   * - In the rest of HTML body replace custom tags to fill the HTML with
   *   the `subject`, `message` and the customizable `button`.
   */
  private val htmlRender = (subject: String, message: String, template: Option[String], button: HtmlButton) => {
    val css = List("viewport", "apple", "main")
      .map(cssFile => cssFile -> Source.fromResource(s"email/css/$cssFile.css").getLines().mkString("\n"))
      .toMap

    val htmlMessageReplacements = List(("\n", "<br/>"), (" ", "&nbsp;"))
    val htmlMessage = htmlMessageReplacements.foldLeft(message) { case (curr, (k, v)) => curr.replace(k, v) }
    val htmlMainReplacements = List(
      ("{{CSS_VIEWPORT}}", css("viewport")),
      ("{{CSS_APPLE}}", css("apple")),
      ("{{CSS_MAIN}}", css("main")),
      ("{{SUBJECT}}", subject),
      ("{{MESSAGE}}", htmlMessage),
      ("{{BUTTON_ENABLED}}", if (button.enable) "visible" else "hidden"),
      ("{{BUTTON_NAME}}", button.name.getOrElse("")),
      ("{{BUTTON_LINK}}", button.link.getOrElse(""))
    )

    Source
      .fromResource(template match {
        case Some(value) => s"email/$value.html"
        case None => s"email/main.html"
      })
      .getLines()
      .map(line => htmlMainReplacements.foldLeft(line) { case (curr, (k, v)) => curr.replace(k, v) })
      .mkString("\n")
  }

}
