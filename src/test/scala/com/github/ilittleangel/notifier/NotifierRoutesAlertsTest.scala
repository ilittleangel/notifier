package com.github.ilittleangel.notifier

import java.net.InetAddress
import java.time.Instant

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.`Remote-Address`
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, RemoteAddress}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.util.Timeout
import com.github.ilittleangel.notifier.destinations.Ftp
import com.github.ilittleangel.notifier.server.NotifierRoutes
import com.github.ilittleangel.notifier.utils.Eithers.separator
import com.stephenn.scalatest.circe.JsonMatchers
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.duration.DurationInt


@RunWith(classOf[JUnitRunner])
class NotifierRoutesAlertsTest extends AnyWordSpec
  with Matchers with ScalatestRouteTest with JsonMatchers with NotifierRoutes with BeforeAndAfterAll with MockFtp {

  private val routes = notifierRoutes
  private val ts = Instant.now()
  private val tsWithFormat = formatter.format(ts)

  override def beforeAll(): Unit = {
    val config = ConfigFactory.empty()
      .withValue("ftp.username", ConfigValueFactory.fromAnyRef(username))
      .withValue("ftp.password", ConfigValueFactory.fromAnyRef(password))

    Ftp.configure(config)
    ftpServer.start()
  }

  override def afterAll(): Unit = {
    ftpServer.stop()
  }

  private implicit val testTimeout: RouteTestTimeout = RouteTestTimeout(Timeout(10.seconds).duration)

  s"NotifierRoutes ($alertsEndpoint)" should {

    s"GET '/$basePath/$alertsEndpoint' return an empty list of alerts" in {
      val request = Get(uri = s"/$basePath/$alertsEndpoint")

      request ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson("[]")
      }

      alerts should have size 0
    }

    s"POST '/$basePath/$alertsEndpoint' be able to reject Slack alerts with no properties" in {
      val requestBody =
        s"""
           |{
           |   "destination": "slack",
           |   "message": "alarm process"
           |}
           |""".stripMargin

      val httpEntity = HttpEntity(ContentTypes.`application/json`, requestBody)
      val request = Post(uri = s"/$basePath/$alertsEndpoint", httpEntity)

      request ~> routes ~> check {
        status shouldBe BadRequest
        responseAs[String] should matchJson(
          s"""
            |{
            |   "status": ${BadRequest.intValue},
            |   "statusText": "${BadRequest.reason}",
            |   "reason": "Slack alert with no properties",
            |   "possibleSolution": "Include properties with 'webhook' url"
            |}
            |""".stripMargin)
      }

      alerts should have size 0
    }

    s"POST '/$basePath/$alertsEndpoint' be able to reject ftp alerts with no properties" in {
      val requestBody =
        s"""
           |{
           |   "destination": "Ftp",
           |   "message": "alarm process"
           |}
           |""".stripMargin

      val httpEntity = HttpEntity(ContentTypes.`application/json`, requestBody)
      val request = Post(uri = s"/$basePath/$alertsEndpoint", httpEntity)

      request ~> routes ~> check {
        status shouldBe BadRequest
        responseAs[String] should matchJson(
          s"""
            |{
            |   "status": ${BadRequest.intValue},
            |   "statusText": "${BadRequest.reason}",
            |   "reason": "Ftp alert with no properties",
            |   "possibleSolution": "Include properties with 'host' and 'path'"
            |}
            |""".stripMargin)
      }

      alerts should have size 0
    }

    s"POST '/$basePath/$alertsEndpoint' be able to accept successful alerts" in {
        val requestBody =
          s"""
             |{
             |   "destination": "Ftp",
             |   "message": "alarm process 1",
             |   "properties": {
             |       "host": "localhost",
             |       "port": "$port",
             |       "protocol": "ftp",
             |       "path": "$homeDirectory/data.txt"
             |   },
             |   "ts": "$tsWithFormat"
             |}
             |""".stripMargin

        val httpEntity = HttpEntity(ContentTypes.`application/json`, requestBody)
        val request = Post(uri = s"/$basePath/$alertsEndpoint", httpEntity)

        // checking HTTP response
        request ~> routes ~> check {
          status shouldBe OK
          contentType shouldBe ContentTypes.`application/json`
          responseAs[String] should matchJson(
            s"""
               |{
               |   "alert": {
               |      "destination": ["ftp"],
               |      "message": "alarm process 1",
               |      "properties": {
               |          "host": "localhost",
               |          "port": "$port",
               |          "protocol": "ftp",
               |          "path": "$homeDirectory/data.txt"
               |      },
               |      "ts": "$tsWithFormat"
               |   },
               |   "isPerformed": true,
               |   "status": "Ftp alert success [value=Done, count=16]",
               |   "description": "$AlertPerformed"
               |}
               |""".stripMargin)
        }

        // checking alerts memory storage
        alerts should have size 1
        alerts.last.isPerformed shouldBe true
        alerts.last.description shouldBe AlertPerformed
        alerts.last.status shouldBe "Ftp alert success [value=Done, count=16]"
        alerts.last.alert.destination shouldBe List(Ftp)
        alerts.last.alert.ts shouldBe Some(ts)
        alerts.last.alert.message shouldBe "alarm process 1"

        // checking ftp destination
        val fileContent = ftpServer.readFile(s"$homeDirectory/data.txt")
        fileContent should include("something before")
        fileContent should include("alarm process 1")
    }

    s"POST '/$basePath/$alertsEndpoint' be able to accept alerts with no timestamp using the default" in {
        val requestBody =
          s"""
             |{
             |   "destination": "Ftp",
             |   "message": "alarm process 2",
             |   "properties": {
             |       "host": "localhost",
             |       "port": "$port",
             |       "protocol": "ftp",
             |       "path": "$homeDirectory/data.txt"
             |   }
             |}
             |""".stripMargin

        val httpEntity = HttpEntity(ContentTypes.`application/json`, requestBody)
        val request = Post(uri = s"/$basePath/$alertsEndpoint", httpEntity)

        // checking HTTP response
        request ~> routes ~> check {
          status shouldBe OK
          contentType shouldBe ContentTypes.`application/json`
          responseAs[String] should matchJson(
            s"""
               |{
               |   "alert": {
               |      "destination": ["ftp"],
               |      "message": "alarm process 2",
               |      "properties": {
               |          "host": "localhost",
               |          "port": "$port",
               |          "protocol": "ftp",
               |          "path": "$homeDirectory/data.txt"
               |      },
               |      "ts": "${formatter.format(alerts.last.alert.ts.get)}"
               |   },
               |   "isPerformed": true,
               |   "status": "Ftp alert success [value=Done, count=16]",
               |   "description": "$AlertPerformed"
               |}
               |""".stripMargin)
        }

        // checking alerts memory storage
        alerts should have size 2
        alerts.last.isPerformed shouldBe true
        alerts.last.description shouldBe AlertPerformed
        alerts.last.status shouldBe "Ftp alert success [value=Done, count=16]"
        alerts.last.alert.destination shouldBe List(Ftp)
        alerts.last.alert.message shouldBe "alarm process 2"

        // checking ftp destination
        val fileContent = ftpServer.readFile(s"$homeDirectory/data.txt")
        fileContent should include("something before")
        fileContent should include("alarm process 2")
    }

    s"POST '/$basePath/$alertsEndpoint' be able to accept alerts not performed" in {
        val requestBody =
          s"""
             |{
             |   "destination": "Ftp",
             |   "message": "alarm process 3",
             |   "properties": {
             |       "host": "localhost",
             |       "port": "$port",
             |       "protocol": "bad-protocol",
             |       "path": "$homeDirectory/data.txt"
             |   },
             |   "ts": "$tsWithFormat"
             |}
             |""".stripMargin

        val httpEntity = HttpEntity(ContentTypes.`application/json`, requestBody)
        val request = Post(uri = s"/$basePath/$alertsEndpoint", httpEntity)

        // checking HTTP response
        request ~> routes ~> check {
          status shouldBe BadRequest
          contentType shouldBe ContentTypes.`application/json`
          responseAs[String] should matchJson(
            s"""
               |{
               |   "alert": {
               |      "destination": ["ftp"],
               |      "message": "alarm process 3",
               |      "properties": {
               |          "host": "localhost",
               |          "port": "$port",
               |          "protocol": "bad-protocol",
               |          "path": "$homeDirectory/data.txt"
               |      },
               |      "ts": "$tsWithFormat"
               |   },
               |   "isPerformed": false,
               |   "status": "Ftp alert failed with an Exception [error=unsupported protocol]",
               |   "description": "$AlertNotPerformed"
               |}
               |""".stripMargin)
        }

        // checking alerts memory storage
        alerts should have size 3
        alerts.last.isPerformed shouldBe false
        alerts.last.description shouldBe AlertNotPerformed
        alerts.last.status shouldBe "Ftp alert failed with an Exception [error=unsupported protocol]"
        alerts.last.alert.destination shouldBe List(Ftp)
        alerts.last.alert.message shouldBe "alarm process 3"

        // checking ftp destination
        val fileContent = ftpServer.readFile(s"$homeDirectory/data.txt")
        fileContent should include("something before")
        fileContent shouldNot include("alarm process 3")
    }

    s"GET '/$basePath/$alertsEndpoint' return a list of alerts in reverse order in which they were received" in {
      val request = Get(uri = s"/$basePath/$alertsEndpoint")

      request ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson(
          s"""
            |[
            |   {
            |      "alert": {
            |         "destination": ["ftp"],
            |         "message": "alarm process 3",
            |         "properties": {
            |             "host": "localhost",
            |             "port": "$port",
            |             "protocol": "bad-protocol",
            |             "path": "$homeDirectory/data.txt"
            |         },
            |         "ts": "$tsWithFormat"
            |      },
            |      "isPerformed": false,
            |      "status": "Ftp alert failed with an Exception [error=unsupported protocol]",
            |      "description": "$AlertNotPerformed"
            |   },
            |   {
            |      "alert": {
            |         "destination": ["ftp"],
            |         "message": "alarm process 2",
            |         "properties": {
            |             "host": "localhost",
            |             "port": "$port",
            |             "protocol": "ftp",
            |             "path": "$homeDirectory/data.txt"
            |         },
            |         "ts": "${formatter.format(alerts(1).alert.ts.get)}"
            |      },
            |      "isPerformed": true,
            |      "status": "Ftp alert success [value=Done, count=16]",
            |      "description": "$AlertPerformed"
            |   },
            |   {
            |      "alert": {
            |         "destination": ["ftp"],
            |         "message": "alarm process 1",
            |         "properties": {
            |             "host": "localhost",
            |             "port": "$port",
            |             "protocol": "ftp",
            |             "path": "$homeDirectory/data.txt"
            |         },
            |         "ts": "$tsWithFormat"
            |      },
            |      "isPerformed": true,
            |      "status": "Ftp alert success [value=Done, count=16]",
            |      "description": "$AlertPerformed"
            |   }
            |]
            |""".stripMargin)
      }
    }

    s"POST '/$basePath/$alertsEndpoint' can be catch the remote client IP" in {
      val requestBody =
        s"""
           |{
           |   "destination": "slack",
           |   "message": "whatever"
           |}
           |""".stripMargin
      val request = Post(uri = s"/$basePath/$alertsEndpoint", HttpEntity(ContentTypes.`application/json`, requestBody))
        .withHeaders(`Remote-Address`(RemoteAddress(InetAddress.getByName("localhost"))))

      request ~> routes ~> check {
        status shouldBe BadRequest
        responseAs[String] should matchJson(
          s"""
             |{
             |   "status": ${BadRequest.intValue},
             |   "statusText": "${BadRequest.reason}",
             |   "reason": "Slack alert with no properties",
             |   "possibleSolution": "Include properties with 'webhook' url",
             |   "clientIp": "localhost - 127.0.0.1"
             |}
             |""".stripMargin)
      }
    }

    s"DELETE '/$basePath/$alertsEndpoint' should reset in-memory alerts and return an empty list" in {
      val request = Delete(uri = s"/$basePath/$alertsEndpoint")

      request ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson("[]")
      }

      alerts should have size 0
    }

    s"POST '/$basePath/$alertsEndpoint' be able to accept multi destination alerts" in {
        val alarmMessage = "alarm process 4"
        val requestBody =
          s"""
             |{
             |   "destination": ["ftp", "ftp"],
             |   "message": "$alarmMessage",
             |   "properties": {
             |          "host": "localhost",
             |          "port": "$port",
             |          "protocol": "ftp",
             |          "path": "$homeDirectory/data.txt"
             |   },
             |   "ts": "$tsWithFormat"
             |}
             |""".stripMargin

        val httpEntity = HttpEntity(ContentTypes.`application/json`, requestBody)
        val request = Post(uri = s"/$basePath/$alertsEndpoint", httpEntity)

        // checking HTTP response
        request ~> routes ~> check {
          status shouldBe OK
          contentType shouldBe ContentTypes.`application/json`
          responseAs[String] should matchJson(
            s"""
               |{
               |   "alert": {
               |      "destination": [
               |          "ftp",
               |          "ftp"
               |      ],
               |      "message": "$alarmMessage",
               |      "properties": {
               |          "host": "localhost",
               |          "port": "$port",
               |          "protocol": "ftp",
               |          "path": "$homeDirectory/data.txt"
               |      },
               |      "ts": "$tsWithFormat"
               |   },
               |   "isPerformed": true,
               |   "status": "Ftp alert success [value=Done, count=16]; Ftp alert success [value=Done, count=16]",
               |   "description": "$AlertPerformed"
               |}
               |""".stripMargin)
        }

        // checking alerts in-memory
        alerts should have size 1
        alerts.last.isPerformed shouldBe true
        alerts.last.description shouldBe AlertPerformed
        alerts.last.status.split(separator).length shouldBe 2
        alerts.last.status shouldBe "Ftp alert success [value=Done, count=16]; Ftp alert success [value=Done, count=16]"
        alerts.last.alert.destination shouldBe List(Ftp, Ftp)
        alerts.last.alert.message shouldBe alarmMessage

        // checking ftp destination
        val fileContent = ftpServer.readFile(s"$homeDirectory/data.txt")
        fileContent.toSeq.sliding(alarmMessage.length).map(_.unwrap).count(_ == alarmMessage) shouldBe 2
    }

    s"POST '/$basePath/$alertsEndpoint' return a Not Performed alert if one or more multi destinations fail" in {
      val requestBody =
        s"""
           |{
           |   "destination": ["ftp", "ftp"],
           |   "message": "alarm process 5",
           |   "properties": {
           |      "host": "localhost",
           |      "port": "$port",
           |      "protocol": "bad-protocol",
           |      "path": "$homeDirectory/data.txt"
           |   },
           |   "ts": "$tsWithFormat"
           |}
           |""".stripMargin

      val httpEntity = HttpEntity(ContentTypes.`application/json`, requestBody)
      val request = Post(uri = s"/$basePath/$alertsEndpoint", httpEntity)
      val failedStatus = "Ftp alert failed with an Exception [error=unsupported protocol]"

      // checking HTTP response
      request ~> routes ~> check {
        status shouldBe BadRequest
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson(
          s"""
             |{
             |   "alert": {
             |      "destination": [
             |          "ftp",
             |          "ftp"
             |      ],
             |      "message": "alarm process 5",
             |      "properties": {
             |          "host": "localhost",
             |          "port": "$port",
             |          "protocol": "bad-protocol",
             |          "path": "$homeDirectory/data.txt"
             |      },
             |      "ts": "$tsWithFormat"
             |   },
             |   "isPerformed": false,
             |   "status": "$failedStatus$separator$failedStatus",
             |   "description": "$AlertNotPerformed"
             |}
             |""".stripMargin)
      }

      // checking alerts in-memory
      alerts should have size 2
      alerts.last.isPerformed shouldBe false
      alerts.last.description shouldBe AlertNotPerformed
      alerts.last.status shouldBe failedStatus + separator + failedStatus
      alerts.last.alert.destination shouldBe List(Ftp, Ftp)
      alerts.last.alert.message shouldBe "alarm process 5"
    }

  }

}
