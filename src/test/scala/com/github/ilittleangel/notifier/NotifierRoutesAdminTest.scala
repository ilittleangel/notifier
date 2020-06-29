package com.github.ilittleangel.notifier

import java.time.Instant

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.util.Timeout
import com.github.ilittleangel.notifier.server.NotifierRoutes
import com.github.ilittleangel.notifier.utils.FixedList
import com.stephenn.scalatest.circe.JsonMatchers
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.duration.DurationInt


@RunWith(classOf[JUnitRunner])
class NotifierRoutesAdminTest extends AnyWordSpec
  with Matchers with ScalatestRouteTest with JsonMatchers with NotifierRoutes with BeforeAndAfterAll {

  private val routes = notifierRoutes
  private val ts = Instant.now()
  private val tsWithFormat = formatter.format(ts)

  private implicit val testTimeout: RouteTestTimeout = RouteTestTimeout(Timeout(10.seconds).duration)

  s"NotifierRoutes ($adminEndpoint)" should {

    s"POST '/$basePath/$adminEndpoint' change the capacity of the in-memory alerts list" in {
      val request = Post(uri = s"/$basePath/$adminEndpoint/set-alerts-capacity?capacity=10")

      // checking HTTP response
      request ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson(
          s"""
             |{
             |    "reason": "Request of change in-memory alerts list capacity to 10",
             |    "status": "200 OK"
             |}
             |""".stripMargin)
      }

      // checking alerts in-memory
      alerts should have size 0
    }

    s"POST '/$basePath/$adminEndpoint' truncate the current alerts list if it is greater" in {
      /*
       * Steps to test this behaviour:
       * 1. empty the alerts list
       * 2. simulate alerts requests (the current capacity is 10 because of the previous test)
       * 3. change the capacity to 5 and check
       */

      // 1
      alerts = new FixedList[AlertPerformed](capacity = 3)
      alerts should have size 0

      // 2
      (1 to 5).foreach { i =>
        val requestBody =
          s"""
             |{
             |   "destination": "ftp",
             |   "message": "alarm process $i",
             |   "properties": {},
             |   "ts": "$tsWithFormat"
             |}
             |""".stripMargin

        val httpEntity = HttpEntity(ContentTypes.`application/json`, requestBody)
        Post(uri = s"/$basePath/$alertsEndpoint", httpEntity) ~> routes
      }

      alerts should have size 3

      // 3
      Post(uri = s"/$basePath/$adminEndpoint/set-alerts-capacity?capacity=2") ~> routes

      alerts should have size 2
    }

    s"POST '/$basePath/$adminEndpoint' be able to reject change an unknown logging level" in {
      val request = Post(uri = s"/$basePath/$adminEndpoint/logging?level=unknown")
      request ~> routes ~> check {
        status shouldBe BadRequest
        contentType shouldBe ContentTypes.`application/json`
        responseAs[ErrorResponse] shouldBe ErrorResponse(
          status = BadRequest,
          reason = "Request of change logging level to 'unknown'",
          possibleSolution = Some("Logging level must be one of [off|error|info|debug|warning]"),
          clientIp = None
        )
      }
    }

    s"POST '/$basePath/$adminEndpoint' be able to change the logging level to OFF" in {
      val request = Post(uri = s"/$basePath/$adminEndpoint/logging?level=off")
      request ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[SuccessResponse] shouldBe SuccessResponse(
          status = OK,
          reason = "Request of change logging level to 'OFF'",
          clientIp = None
        )
      }
      log.isInfoEnabled shouldBe false
    }

    s"POST '/$basePath/$adminEndpoint' be able to change the logging level to ERROR" in {
      val request = Post(uri = s"/$basePath/$adminEndpoint/logging?level=error")
      request ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[SuccessResponse] shouldBe SuccessResponse(
          status = OK,
          reason = "Request of change logging level to 'ERROR'",
          clientIp = None
        )
      }
      log.isInfoEnabled shouldBe false
      log.isErrorEnabled shouldBe true
    }

    s"POST '/$basePath/$adminEndpoint' be able to change the logging level to WARNING" in {
      val request = Post(uri = s"/$basePath/$adminEndpoint/logging?level=warning")
      request ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[SuccessResponse] shouldBe SuccessResponse(
          status = OK,
          reason = "Request of change logging level to 'WARNING'",
          clientIp = None
        )
      }
      log.isWarningEnabled shouldBe true
    }

    s"POST '/$basePath/$adminEndpoint' be able to change the logging level to INFO" in {
      val request = Post(uri = s"/$basePath/$adminEndpoint/logging?level=info")
      request ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[SuccessResponse] shouldBe SuccessResponse(
          status = OK,
          reason = "Request of change logging level to 'INFO'",
          clientIp = None
        )
      }
      log.isInfoEnabled shouldBe true
    }

    s"POST '/$basePath/$adminEndpoint' be able to change the logging level to DEBUG" in {
      val request = Post(uri = s"/$basePath/$adminEndpoint/logging?level=debug")
      request ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[SuccessResponse] shouldBe SuccessResponse(
          status = OK,
          reason = "Request of change logging level to 'DEBUG'",
          clientIp = None
        )
      }
      log.isDebugEnabled shouldBe true
    }

    s"GET '/$basePath/$adminEndpoint' return the current logging level ERROR" in {
      Post(uri = s"/$basePath/$adminEndpoint/logging?level=error") ~> routes
      Get(uri = s"/$basePath/$adminEndpoint/logging") ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson(
          """
            |{
            |   "level": "ERROR"
            |}
            |""".stripMargin)
      }
    }

    s"GET '/$basePath/$adminEndpoint' return the current logging level WARNING" in {
      Post(uri = s"/$basePath/$adminEndpoint/logging?level=warning") ~> routes
      Get(uri = s"/$basePath/$adminEndpoint/logging") ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson(
          """
            |{
            |   "level": "WARNING"
            |}
            |""".stripMargin)
      }
    }

    s"GET '/$basePath/$adminEndpoint' return the current logging level INFO" in {
      Post(uri = s"/$basePath/$adminEndpoint/logging?level=info") ~> routes
      Get(uri = s"/$basePath/$adminEndpoint/logging") ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson(
          """
            |{
            |   "level": "INFO"
            |}
            |""".stripMargin)
      }
    }

    s"GET '/$basePath/$adminEndpoint' return the current logging level DEBUG" in {
      Post(uri = s"/$basePath/$adminEndpoint/logging?level=debug") ~> routes
      Get(uri = s"/$basePath/$adminEndpoint/logging") ~> routes ~> check {
        status shouldBe OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson(
          """
            |{
            |   "level": "DEBUG"
            |}
            |""".stripMargin)
      }
    }

  }

}
