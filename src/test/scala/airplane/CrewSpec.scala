package airplane

import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import akka.actor.{ActorRef, Actor, Props, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers
import airplane.FlightAttendant.{Drink, GetDrink}
import com.typesafe.config.ConfigFactory
import airplane.LeadFlightAttendant.{Attendant, GetFlightAttendant}
import utils.{IsolatedLifeCycleSupervisor, OneForOneStrategyFactory, IsolatedStopSupervisor}
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import airplane.Pilots.ReadyToGo
import helper.ActorSys

object TestFlightAttendant {
  def apply() = new FlightAttendant with AttendantResponsiveness {
    val maxResponseTimeMS = 1
  }
}

object TestLeadFlightAttendant {
  def apply(): LeadFlightAttendant = new LeadFlightAttendant with AttendantCreationPolicy {
    override val numberOfAttendants = 1

    override def createAttendant = TestFlightAttendant()
  }
}

class CrewSpec extends TestKit(ActorSystem("CrewSpec", ConfigFactory.parseString("akka.scheduler.tick-duration = 1ms"))) with ImplicitSender with WordSpec with MustMatchers with BeforeAndAfterAll {

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

object PilotsSpec {
  val fakeCopilotName = "Jane"
  val copilotName = "Mary"
  val pilotName = "Mark"
  val configStr = s"""
zzz.akka.avionics.flightcrew.copilotName = "$copilotName"
zzz.akka.avionics.flightcrew.pilotName = "$pilotName" """
}

class PilotsSpec extends TestKit(ActorSystem("PilotsSpec",
  ConfigFactory.parseString(PilotsSpec.configStr)))
with ImplicitSender
with WordSpec
with MustMatchers {

  import PilotsSpec._
  import Plane._

  "CoPilot" should {
    "take control when the Pilot dies" in {
      implicit val actorSys: SysProp = SysProp("CoPilotTest")

      pilotsReadyToGo
      system.actorFor(copilotPath) ! Pilots.ReadyToGo
      // Kill the Pilot
      system.actorFor(pilotPath) ! "throw"
      // Since the test class is the "Plane" we can
      // expect to see this request
      expectMsg(GiveMeControl)
      // The girl who sent it had better be Mary
      lastSender must be(system.actorFor(copilotPath))
    }
  }

  "Autopilot" should {
    "take control when che Copilot dies" in {
      implicit val actorSys: SysProp = SysProp("AutoPilotTest")

      pilotsReadyToGo

      val autopilot = system.actorOf(Props(new AutoPilot(testActor)))
      val fakeCopilot = system.actorFor(fakeCopilotPath)

      autopilot ! ReadyToGo

      expectMsg(WhoIsCopilot)
      lastSender ! Copilot(fakeCopilot)

      fakeCopilot ! "die!"

      expectMsg(GiveMeControl)
      lastSender must be(autopilot)
    }
  }

  case class SysProp(name: String)

  def nilActor = system.actorOf(Props[NilActor])

  def pilotPath(implicit actorSys: SysProp) = s"/user/${actorSys.name}/$pilotName"

  def copilotPath(implicit actorSys: SysProp) = s"/user/${actorSys.name}/$copilotName"

  def fakeCopilotPath(implicit actorSys: SysProp) = s"/user/${actorSys.name}/$fakeCopilotName"


  def pilotsReadyToGo(implicit actorSys: SysProp): Unit = {
    // The 'ask' below needs a timeout value
    implicit val askTimeout = Timeout(4.seconds)
    // Much like the creation we're using in the Plane
    val a = system.actorOf(Props(new IsolatedStopSupervisor
      with OneForOneStrategyFactory {
      def childStarter() {
        context.actorOf(Props[FakePilot], pilotName)
        context.actorOf(Props[FakePilot], fakeCopilotName)
        context.actorOf(Props(new CoPilot(testActor, nilActor,
          nilActor)), copilotName)
      }
    }), actorSys.name)
    // Wait for the mailboxes to be up and running for the children
    Await.result(a ? IsolatedLifeCycleSupervisor.WaitForStart, 3.seconds)
    // Tell the CoPilot that it's ready to go

  }
}

class FakePilot extends Actor {
  override def receive = {
    case _ =>
      throw new Exception("This exception is expected.")
  }
}

class NilActor extends Actor {
  def receive = {
    case _ =>
  }
}