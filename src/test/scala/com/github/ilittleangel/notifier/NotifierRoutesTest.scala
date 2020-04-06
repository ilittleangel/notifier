package com.github.ilittleangel.notifier

import java.time.Instant

import akka.actor.ActorRef
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.github.ilittleangel.notifier.server.{NotifierActor, NotifierRoutes}
import com.stephenn.scalatest.circe.JsonMatchers
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}


@RunWith(classOf[JUnitRunner])
class NotifierRoutesTest extends WordSpec with Matchers with ScalatestRouteTest with JsonMatchers with NotifierRoutes {

  override val notifierActor: ActorRef = system.actorOf(NotifierActor.props, "NotifierActorTest")
  lazy val routes: Route = notifierRoutes
  private val ts = Instant.now()
  private val basePath = "/notifier/api/v1"

  "NotifierRoutes" should {

    "GET '/argos/api/v1/alert' return an empty list of alerts" in {
      val request = Get(uri = s"$basePath/alert")

      request ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson(
          """
             {
                "alerts": []
             }
          """)
      }
    }

    "POST '/argos/api/v1/alert' be able to add alerts" in {
      val requestBody =
        s"""
           {
              "destination": "slack",
              "message": "alarm process 1",
              "ts": "$ts"
           }
        """
      val httpEntity = HttpEntity(ContentTypes.`application/json`, requestBody)
      val request = Post(uri = s"$basePath/alert", httpEntity)

      request ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson(
          s"""
             {
                "description": "Slack alert sent!",
                "destination": "slack",
                "message": "alarm process 1",
                "ts": "$ts"
             }
          """)
      }
    }

    "POST '/argos/api/v1/alert' be able to add alerts with no timestamp" in {
      val requestBody =
        """
           {
              "destination": "Tivoli",
              "message": "alarm process 2"
           }
        """
      val httpEntity = HttpEntity(ContentTypes.`application/json`, requestBody)
      val request = Post(uri = s"$basePath/alert", httpEntity)

      request ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson(
          s"""
             {
                "description": "Tivoli alert with no 'ts' sent! Instant.now will be use.",
                "destination": "tivoli",
                "message": "alarm process 2",
                "ts": "${NotifierActor.defaultTs}"
             }
          """
        )
      }
    }

    "GET '/argos/api/v1/alert' return a list of alerts in reverse order of how they were received" in {
      val request = Get(uri = s"$basePath/alert")

      request ~> routes ~> check {
        status shouldBe StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`
        responseAs[String] should matchJson(
          s"""
             {
                "alerts": [
                    {
                        "destination": "tivoli",
                        "message": "alarm process 2",
                        "ts": "${NotifierActor.defaultTs}"
                    },
                    {
                        "destination": "slack",
                        "message": "alarm process 1",
                        "ts": "$ts"
                    }
                ]
             }
          """)
      }
    }

  }

}

