package com.github.ilittleangel.notifier

import java.io.{ByteArrayOutputStream, IOException}

import org.apache.commons.net.ftp.FTPClient
import org.mockftpserver.fake.{FakeFtpServer, UserAccount}
import org.mockftpserver.fake.filesystem.{FileEntry, UnixFakeFileSystem}

trait MockFtp {
  val username = "notifier"
  val password = "password"
  val port = 2223
  val homeDirectory = "/tmp"
  val ftpServer: FakeFtpServer = new FakeFtpServer
  val ftpFileSystem = new UnixFakeFileSystem

  ftpFileSystem.add(new FileEntry(s"$homeDirectory/data.txt", "something before"))
  ftpServer.setServerControlPort(port)
  ftpServer.addUserAccount(new UserAccount(username, password, homeDirectory))
  ftpServer.setFileSystem(ftpFileSystem)

  implicit class FakeFtpServerOps(server: FakeFtpServer) {
    @throws[IOException]
    def readFile(filename: String): String = {
      val ftpClient = new FTPClient
      ftpClient.connect("localhost", server.getServerControlPort)
      ftpClient.login(username, password)
      val outputStream = new ByteArrayOutputStream
      val success = ftpClient.retrieveFile(filename, outputStream)
      ftpClient.disconnect()
      if (!success) throw new IOException("Retrieve file failed: " + filename)
      outputStream.toString
    }
  }

}
