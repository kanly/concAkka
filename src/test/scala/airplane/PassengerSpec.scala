package airplane

import scala.concurrent.duration._
import akka.testkit.{ExtractRoute, TestProbe, ImplicitSender, TestKit}
import akka.actor.{Actor, Props, ActorRef, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, WordSpec}
import akka.event.Logging.Info
import airplane.Passenger.FastenSeatbelts
import org.scalatest.matchers.MustMatchers
import com.typesafe.config.ConfigFactory
import akka.routing.RouterConfig

class PassengerSpec extends TestKit(ActorSystem()) with ImplicitSender with WordSpec with MustMatchers {

  var seatNumber = 9

  def newPassenger(): ActorRef = {
    seatNumber += 1
    system.actorOf(Props(new Passenger(testActor) with TestDrinkRequestProbability), s"Pat_Metheny-$seatNumber-B")
  }

  "Passengers" should {
    "fasten seatbelts when asked" in {
      val a = newPassenger()
      val p = TestProbe()
      system.eventStream.subscribe(p.ref, classOf[Info])
      a ! FastenSeatbelts
      p.expectMsgPF() {
        case Info(_, _, m) =>
          m.toString must include("fastening seatbelt")
      }
    }

  }


}


trait TestDrinkRequestProbability extends DrinkRequestProbability {
  override val askThreshold = 0f
  override val requestMin = 0.milliseconds
  override val requestUpper = 2.milliseconds

}

object PassengerSupervisorSpec {
  val config = ConfigFactory.parseString( """
                                            airplane.passengers = [
                                             [ "Kelly Franqui", "23", "A" ],
                                             [ "Tyrone Dotts", "23", "B" ],
                                             [ "Malinda Class", "23", "C" ],
                                             [ "Kenya Jolicoeur", "24", "A" ],
                                             [ "Christian Piche", "24", "B" ]
                                            ]
                                          """.stripMargin)

}

trait TestPassengerProvider extends PassengerProvider {
  override def newPassenger(callButton: ActorRef): Actor =
  // a fake passenger
    new Actor {
      def receive = {
        case m => callButton ! m
      }
    }
}

class PassengerSupervisorSpec extends TestKit(ActorSystem("PassengerSupervisorSpec", PassengerSupervisorSpec.config))
with ImplicitSender with WordSpec with BeforeAndAfterAll with MustMatchers {

  import PassengerSupervisor._

  override def afterAll() {
    system.shutdown()
  }

  "PassengerSupervisor" should {
    "work" in {
      val a = system.actorOf(Props(new PassengerSupervisor(testActor)
        with TestPassengerProvider))

      a ! GetPassengerBroadcaster
      val broadcaster = expectMsgPF() {
        case PassengerBroadcaster(b) =>

          b ! "Hithere"

          expectMsg("Hithere")
          expectMsg("Hithere")
          expectMsg("Hithere")
          expectMsg("Hithere")
          expectMsg("Hithere")

          expectNoMsg(100.milliseconds)

          b
      }

      a ! GetPassengerBroadcaster
      expectMsg(PassengerBroadcaster(`broadcaster`))
    }
  }
}


class TestRoutee extends Actor {
  def receive = {
    case m => sender ! m
  }
}

class RouterRelay extends Actor {
  def receive = {
    case _ =>
  }
}

class SectionSpecificAttendantRouterSpec extends TestKit(ActorSystem("SectionSpecificAttendantRouterSpec")) with ImplicitSender with WordSpec with BeforeAndAfterAll with MustMatchers {

  override def afterAll() {
    system.shutdown()
  }

  def newRouter(): RouterConfig = new SectionSpecificAttendantRouter with FlightAttendantProvider {
    override def newFlightAttendant() = new TestRoutee
  }

  def relayWithRow(row: Int) = system.actorOf(Props[RouterRelay], s"Someone-$row-C")

  val passengers = (1 to 25).map(relayWithRow)
  "SectionSpecificAttendantRouter" should {
    "route consistently" in {
      val router = system.actorOf(Props[TestRoutee].withRouter(newRouter()))
      val route = ExtractRoute(router)
      val routeA = passengers.slice(0, 10).map {
        p => route(p, "Hi")
      }.flatten
      routeA.tail.forall {
        _.recipient == routeA.head.recipient
      } must be(true)
      val routeAB = passengers.slice(9, 11).map {
        p => route(p, "Hi")
      }.flatten
      routeAB.head must not be (routeAB.tail.head)
      val routeB = passengers.slice(10, 20).map {
        p => route(p, "Hi")
      }.flatten
      routeB.tail.forall {
        _.recipient == routeB.head.recipient
      } must be(true)
    }
  }
}

