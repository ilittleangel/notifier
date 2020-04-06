package com.github.ilittleangel.notifier.destinations.ftp

import java.io.{File, PrintWriter}
import java.net.InetAddress

import akka.actor.ActorSystem
import akka.stream.alpakka.ftp.scaladsl.{Ftp, Sftp}
import akka.stream.alpakka.ftp.{FtpCredentials, FtpSettings, SftpSettings}
import akka.stream.scaladsl.{FileIO, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import com.github.ilittleangel.notifier.server.NotifierServer
import org.apache.commons.net.PrintCommandListener
import org.apache.commons.net.ftp.FTPClient

import scala.concurrent.{ExecutionContext, Future}


trait AlpakkaFtpClient {

  /**
   * Public API for uploading files.
   */
  def upload(message: String, path: String, host: String, port: Int, user: String, pass: String, protocol: String): Future[IOResult] = {
    settings(protocol, host, port, user, pass) match {
      case settings: FtpSettings => ftpUpload(message, path, settings)
      case settings: SftpSettings => sftpUpload(message, path, settings)
      case _ => Future.failed(new IllegalStateException("FtpSettings or SftpSettings instance not created"))
    }
  }

  /**
   * Public API for downloading files.
   */
  def download(srcPath: String, destPath: String, protocol: String, host: String, port: Int, user: String, pass: String): Future[IOResult] = {
    settings(protocol, host, port, user, pass) match {
      case settings: FtpSettings => ftpDownload(srcPath, destPath, settings)
      case settings: SftpSettings => sftpDownload(srcPath, destPath, settings)
      case _ => Future.failed(new IllegalStateException("FtpSettings or SftpSettings instance not created"))
    }
  }

  private implicit val system: ActorSystem = NotifierServer.system
  private implicit val materializer: ActorMaterializer = NotifierServer.materializer
  private implicit val executionContext: ExecutionContext = NotifierServer.executionContext

  private val settings = (protocol: String, host: String, port: Int, user: String, pass: String) => protocol.toLowerCase() match {
        case "ftp" => ftpSettings(host, port, user, pass)
        case "sftp" => sftpSettings(host, port, user, pass)
        case _ => throw new IllegalArgumentException("unsupported protocol")
  }

  private lazy val ftpSettings = (host: String, port: Int, user: String, password: String) => {
    FtpSettings
      .create(InetAddress.getByName(host))
      .withPort(port)
      .withCredentials(FtpCredentials.create(user, password))
      .withBinary(true)
      .withPassiveMode(true)
      // only useful for debugging
      .withConfigureConnection((ftpClient: FTPClient) => {
        ftpClient.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out), true))
      })
  }

  private lazy val sftpSettings = (host: String, port: Int, user: String, password: String) => {
    SftpSettings
      .create(InetAddress.getByName(host))
      .withPort(port)
      //.withSftpIdentity(SftpIdentity.createFileSftpIdentity(System.getenv("SFTP_PRIVATE_KEY")))
      .withStrictHostKeyChecking(false)
      .withCredentials(FtpCredentials.create(user, password))
  }

  private def ftpUpload(message: String, path: String, ftpSettings: FtpSettings): Future[IOResult] = {
    Source
      .single(ByteString(message))
      .runWith(Ftp.toPath(path, ftpSettings, append = true))
  }

  private def ftpDownload(srcPath: String, destPath: String, ftpSettings: FtpSettings): Future[IOResult] = {
    Ftp
      .fromPath(srcPath, ftpSettings)
      .runWith(FileIO.toPath(new File(destPath).toPath))
  }

  private def sftpUpload(message: String, path: String, sftpSettings: SftpSettings): Future[IOResult] = {
    Source
      .single(ByteString(message))
      .runWith(Sftp.toPath(path, sftpSettings, append = true))
  }

  private def sftpDownload(srcPath: String, destPath: String, sftpSettings: SftpSettings): Future[IOResult] = {
    Sftp
      .fromPath(srcPath, sftpSettings)
      .runWith(FileIO.toPath(new File(destPath).toPath))
  }


}
