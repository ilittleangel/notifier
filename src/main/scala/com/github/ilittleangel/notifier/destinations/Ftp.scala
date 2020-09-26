package com.github.ilittleangel.notifier.destinations

import java.io.IOException

import akka.stream.IOResult
import com.github.ilittleangel.notifier.config.FtpConfig
import com.github.ilittleangel.notifier.destinations.clients.FtpClient
import com.typesafe.config.Config

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


case object Ftp extends Destination with FtpClient {

  private var ftpConfig: FtpConfig = _

  override def configure(config: Config): Unit = {
    ftpConfig = FtpConfig.apply(config)
  }

  override def send(message: String, props: Map[String, String]): Future[Either[String, String]] = {

    try {
      val user = ftpConfig.user
      val pass = ftpConfig.pass
      val host = Try(props("host")).getOrElse(ftpConfig.host.getOrElse("'host' property not found"))
      val port = Try(props("port")).getOrElse(ftpConfig.port.getOrElse("'port' property not found"))
      val path = Try(props("path")).getOrElse(ftpConfig.path.getOrElse("'path' property not found"))
      val prot = Try(props("protocol")).getOrElse(ftpConfig.protocol.getOrElse("'protocol' property not found"))
      val withPrivateKey = Try(props("withPrivateKey").toBoolean).getOrElse(ftpConfig.withPrivateKey)
      val privateKey = if (withPrivateKey) sys.env.get("SFTP_PRIVATE_KEY") else None

      log.debug(s"Ftp destination with params: " +
        s"user=$user, pass=$pass, host=$host, port=$port, path=$path, protocol=$prot, withPrivateKey=$withPrivateKey")

      upload(s"$message\n", path, host, port.toInt, user, pass, prot, privateKey).flatMap {
        case IOResult(count, Success(value)) =>
          Future.successful(s"Ftp alert success [value=$value, count=$count]").map(Right(_))

        case IOResult(count, Failure(exception)) =>
          Future.successful(Left(s"Ftp alert failed with IOResult[error=${exception.getMessage}, count=$count]"))

        case _ =>
          Future.failed(new IOException("Ftp alert failed with unknown error"))
      }
    }
    catch {
      case e: Exception =>
        Future.successful(Left(s"Ftp alert failed with an Exception [error=${e.getMessage}]"))
    }
  }

}
