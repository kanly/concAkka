package airplane

import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.WordSpec
import org.scalatest.matchers.MustMatchers
import airplane.FlightAttendant.{Drink, GetDrink}

object TestFlightAttendant {
  def apply() = new FlightAttendant with AttendantResponsiveness {
    val maxResponseTimeMS = 1
  }
}

class CrewSpec extends TestKit(ActorSystem("CrewSpec")) with ImplicitSender with WordSpec with MustMatchers {

  "FlighAttendant" should {
    "get a drink when asked" in {
      val act = TestActorRef(Props(TestFlightAttendant()))
      act ! GetDrink("Cola")
      expectMsg(Drink("Cola"))
    }
  }

}
