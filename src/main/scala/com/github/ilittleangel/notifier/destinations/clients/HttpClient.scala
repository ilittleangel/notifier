package com.github.ilittleangel.notifier.destinations.clients

import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.{KeyStore, SecureRandom}

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{ClientTransport, ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.ActorMaterializer
import com.github.ilittleangel.notifier.{Default, HttpSecurity, Insecure, Secure}
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import javax.net.ssl._
import spray.json.JsValue

import scala.concurrent.{ExecutionContext, Future}

trait HttpClient {

  private lazy val log: LoggingAdapter = Logging(system, classOf[HttpClient])

  /**
   * Public API for performing HTTP requests.
   */
  def performHttpRequest(verb: HttpMethod, url: String,
                         payload: Option[JsValue] = None,
                         proxy: Option[String] = None,
                         customName: String = "custom",
                         security: HttpSecurity = Default): Future[Either[String, String]] = {

    val httpRequest = payload match {
      case Some(json) =>
        log.debug(s"Json body request: ${json.prettyPrint}")
        HttpRequest(verb, url, entity = HttpEntity(ContentTypes.`application/json`, json.compactPrint))
      case None =>
        HttpRequest(verb, url)
    }

    Http()
      .singleRequest(httpRequest, connectionContext = httpsContext(security), settings = customSettings(proxy))
      .flatMap(processResponse(_, customName))
  }

  implicit def system: ActorSystem
  implicit def materializer: ActorMaterializer
  implicit def executionContext: ExecutionContext

  private lazy val defaultSettings = ConnectionPoolSettings(system)

  private lazy val customSettings: Option[String] => ConnectionPoolSettings = {
    case Some(proxy) =>
      val Array(host, port) = proxy.split(":")
      defaultSettings.withTransport(ClientTransport.httpsProxy(InetSocketAddress.createUnresolved(host, port.toInt)))
    case None =>
      defaultSettings
  }

  private lazy val httpsContext: HttpSecurity => HttpsConnectionContext = {
    case Default => Http().defaultClientHttpsContext
    case Insecure => createInsecureHttpsContext()
    case Secure(jks, pass) => createSecureHttpsContext(jks, pass)
  }

  private def processResponse(response: HttpResponse, customName: String): Future[Either[String, String]] = {
    response.status match {
      case OK =>
        val msg = s"$customName success [status=${response.status}]"
        Unmarshal(response.entity).to[String].map(res => Right(s"$msg [$res]"))

      case BadRequest | Forbidden | NotFound | Gone =>
        val msg = s"$customName failed [status=${response.status}]"
        Unmarshal(response.entity).to[String].map(res => Left(s"$msg [$res]"))

      case _ =>
        Unmarshal(response.entity).to[String].flatMap { entity =>
          val msg = s"$customName failed with unknown error [status=${response.status}]"
          Future.successful(Left(s"$msg [$entity]"))
        }
    }
  }

  private def createInsecureHttpsContext(): HttpsConnectionContext = {
    Http().createClientHttpsContext(AkkaSSLConfig().mapSettings(s => s.withLoose {
      s.loose
        .withDisableSNI(true)
        .withDisableHostnameVerification(true)
    }))
  }

  private def createSecureHttpsContext(jksPath: String, pass: String): HttpsConnectionContext = {
    val sslContext = SSLContext.getInstance("TLS")
    val (_, keyManagers, trustManagers) = loadCertificates(jksPath, pass)
    sslContext.init(keyManagers, trustManagers, new SecureRandom)

    ConnectionContext.https(sslContext)
  }

  def loadCertificates(jksPath: String, pass: String): (KeyStore, Array[KeyManager], Array[TrustManager]) = {
    val keyStore = KeyStore.getInstance("jks")
    val keyStorePath = new File(jksPath).toPath
    val keyStorePass = pass.toCharArray
    val keyStoreFile = Files.newInputStream(keyStorePath)
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    val trustManagerFactory = TrustManagerFactory.getInstance("SunX509")

    try keyStore.load(keyStoreFile, keyStorePass) finally if (keyStoreFile != null) keyStoreFile.close()
    keyManagerFactory.init(keyStore, keyStorePass)
    trustManagerFactory.init(keyStore)

    (keyStore, keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers)
  }

}
