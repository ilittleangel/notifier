package com.github.ilittleangel.notifier

import com.github.ilittleangel.notifier.destinations.Slack
import com.github.ilittleangel.notifier.utils.{FixedList, FixedListFactory}
import org.junit.runner.RunWith
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class FixedListTest extends AnyWordSpec with Matchers {

  private val mockAlert = Alert(List(Slack), s"message", None, None)
  private val actionPerformed = AlertPerformed(mockAlert, isPerformed = false, "", "", None)

  "FixedList" should {

    "be able to manage a Scala collection with no truncated elements if capacity is not exceeded" in {
      var alerts = new FixedList[AlertPerformed](capacity = 5).empty

      (1 to 10).foreach { i =>
        alerts = alerts :+ actionPerformed.copy(alert = mockAlert.copy(message = s"message_$i"))
      }

      alerts should have size 5
      alerts.head.alert.message shouldBe "message_6"
      alerts.last.alert.message shouldBe "message_10"
    }

    "be able to manage a Scala collection with the first elements truncated if capacity is exceeded" in {
      var alerts = new FixedList[AlertPerformed](capacity = 20).empty

      (1 to 10).foreach { i =>
        alerts = alerts :+ actionPerformed.copy(alert = mockAlert.copy(message = s"message_$i"))
      }

      alerts should have size 10
      alerts.head.alert.message shouldBe "message_1"
      alerts.last.alert.message shouldBe "message_10"
    }

    "be able to be transformed from Scala Standard collections" in {
      object FixedList extends FixedListFactory(capacity = 5)

      val l = List(1, 2, 3, 4, 5, 6)
      val fl = l.to(FixedList)

      l  should have size 6
      fl should have size 5
      fl.head shouldBe 2
      fl.last shouldBe 6
    }

    "be able to be transformed from Scala Standard collections with complex objects" in {
      object FixedList extends FixedListFactory(capacity = 5)

      val alerts = (1 to 20)
        .map { i => actionPerformed.copy(alert = mockAlert.copy(message = s"message_$i")) }
        .to(FixedList)

      alerts should have size 5
      alerts.head.alert.message shouldBe "message_16"
      alerts.last.alert.message shouldBe "message_20"
    }

  }

}
