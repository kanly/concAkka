package airplane

import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.actor.{Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers
import airplane.FlightAttendant.{Drink, GetDrink}
import com.typesafe.config.ConfigFactory
import airplane.LeadFlightAttendant.{Attendant, GetFlightAttendant}
import airplane.Altimeter.AltitudeUpdate

object TestFlightAttendant {
  def apply() = new FlightAttendant with AttendantResponsiveness {
    val maxResponseTimeMS = 1
  }
}

object TestLeadFlightAttendant {
  def apply():LeadFlightAttendant = new LeadFlightAttendant with AttendantCreationPolicy {
    override val numberOfAttendants = 1

    override def createAttendant = TestFlightAttendant()
  }
}

class CrewSpec extends TestKit(ActorSystem("CrewSpec", ConfigFactory.parseString("akka.scheduler.tick-duration = 1ms"))) with ImplicitSender with WordSpec with MustMatchers  with BeforeAndAfterAll {

  override def afterAll() {
    system.shutdown()
  }

  "FlighAttendant" should {
    "get a drink when asked" in {
      val act = TestActorRef(Props(TestFlightAttendant()))
      act ! GetDrink("Cola")
      expectMsg(Drink("Cola"))
    }
  }

  "LeadFlighAttendant" should {
    import airplane.FlightAttendant

    "give an attendant when asked" in {
      val act = TestActorRef(Props(TestLeadFlightAttendant()))
      act ! GetFlightAttendant
      fishForMessage() {
        case Attendant(_) => true
      }
    }

    "forward message to an attendant" in {
      val act = TestActorRef(Props(TestLeadFlightAttendant()))
      act ! GetDrink("Cola")
      expectMsg(Drink("Cola"))
    }
  }

}
