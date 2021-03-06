package com.github.ilittleangel.notifier.destinations.clients

import java.io.{File, PrintWriter}
import java.net.InetAddress

import akka.actor.ActorSystem
import akka.stream.alpakka.ftp.scaladsl.{Ftp, Sftp}
import akka.stream.alpakka.ftp.{FtpCredentials, FtpSettings, SftpIdentity, SftpSettings}
import akka.stream.scaladsl.{FileIO, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import org.apache.commons.net.PrintCommandListener
import org.apache.commons.net.ftp.FTPClient

import scala.concurrent.{ExecutionContext, Future}


trait FtpClient {

  /**
   * Public API for uploading files.
   */
  def upload(message: String, path: String,
             host: String, port: Int,
             user: String, pass: String,
             protocol: String, privateKey: Option[String]): Future[IOResult] = {

    settings(protocol, host, port, user, pass, privateKey) match {
      case settings: FtpSettings => ftpUpload(message, path, settings)
      case settings: SftpSettings => sftpUpload(message, path, settings)
      case _ => Future.failed(new IllegalStateException("FtpSettings or SftpSettings instance not created"))
    }
  }

  /**
   * Public API for downloading files.
   */
  def download(srcPath: String, destPath: String,
               host: String, port: Int,
               user: String, pass: String,
               protocol: String, privateKey: Option[String]): Future[IOResult] = {

    settings(protocol, host, port, user, pass, privateKey) match {
      case settings: FtpSettings => ftpDownload(srcPath, destPath, settings)
      case settings: SftpSettings => sftpDownload(srcPath, destPath, settings)
      case _ => Future.failed(new IllegalStateException("FtpSettings or SftpSettings instance not created"))
    }
  }

  implicit def system: ActorSystem
  implicit def materializer: ActorMaterializer
  implicit def executionContext: ExecutionContext

  private val settings = (protocol: String, host: String, port: Int, user: String, pass: String, privateKey: Option[String]) =>
    protocol.toLowerCase() match {
      case "ftp" => ftpSettings(host, port, user, pass)
      case "sftp" => sftpSettings(host, port, user, pass, privateKey)
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

  private lazy val sftpSettings = (host: String, port: Int, user: String, password: String, privateKey: Option[String]) => {
    val settings = SftpSettings
      .create(InetAddress.getByName(host))
      .withPort(port)
      .withStrictHostKeyChecking(false)

    privateKey match {
      case Some(key) => settings.withSftpIdentity(SftpIdentity.createFileSftpIdentity(key))
      case None => settings.withCredentials(FtpCredentials.create(user, password))
    }
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
