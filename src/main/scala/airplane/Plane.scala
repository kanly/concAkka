package airplane

import akka.actor.{Props, ActorLogging, Actor, ActorRef}
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import airplane.Altimeter.AltitudeUpdate
import utils.IsolatedLifeCycleSupervisor.WaitForStart

object Plane {

  case object GiveMeControl

  case class Controls(controlSurfaces: ActorRef)

  case object WhoIsCopilot

  case class Copilot(copilot: ActorRef)

}

class Plane extends Actor with ActorLogging {
  this: AltimeterProvider
    with PilotProvider with LeadFlightAttendantProvider =>

  import Plane._
  import ProductionEventSource._
  import utils.{IsolatedStopSupervisor, IsolatedResumeSupervisor, OneForOneStrategyFactory}
  import akka.pattern.ask

  val altimeter = context.actorOf(Props[Altimeter])
  val controls = context.actorOf(Props(new ControlSurfaces(altimeter)))
  val config = context.system.settings.config
  lazy val pilotName = config.getString("airplane.flightcrew.pilotName")
  lazy val copilotName = config.getString("airplane.flightcrew.copilotName")
  lazy val leadAttendantName = config.getString("airplane.flightcrew.leadAttendantName")
  val pilot = context.actorOf(Props[Pilot], pilotName)
  val copilot = context.actorOf(Props[CoPilot], copilotName)
  val autopilot = context.actorOf(Props[AutoPilot], "AutoPilot")
  val flightAttendant = context.actorOf(Props(LeadFlightAttendant()), leadAttendantName)

  def receive = {
    case AltitudeUpdate(altitude) =>
      log.info(s"Altitude is now: $altitude")
    case GiveMeControl =>
      log.info("Plane giving control")
      sender ! Controls(controls)
    case WhoIsCopilot =>
      log.info("Plane returning copilot")
      sender ! Copilot(copilot)
  }

  override def preStart() {
    startControls()
    startPeople()

    actorForControls("Altimeter") ! RegisterListener(self)
    actorForPilots(pilotName) ! Pilots.ReadyToGo
    actorForPilots(copilotName) ! Pilots.ReadyToGo
  }

  def actorForControls(name: String) = context.actorFor("Controls/" + name)

  def actorForPilots(name: String) = context.actorFor("Pilots/" + name)

  def startControls() {
    implicit val timeout = Timeout(5 seconds)
    val controls = context.actorOf(Props(new IsolatedResumeSupervisor with OneForOneStrategyFactory {
      def childStarter() {
        val alt = context.actorOf(Props(newAltimeter), "Altimeter")
        context.actorOf(Props(newAutopilot(self)), "AutoPilot")
        context.actorOf(Props(new ControlSurfaces(alt)), "ControlSurfaces")
      }
    }), "Controls")
    Await.result(controls ? WaitForStart, timeout.duration)
  }

  def startPeople() {
    implicit val timeout = Timeout(5 seconds)
    val plane = self
    val controls = actorForControls("ControlSurfaces")
    val autopilot = actorForControls("AutoPilot")
    val altimeter = actorForControls("Altimeter")
    val people = context.actorOf(Props(new IsolatedStopSupervisor() with OneForOneStrategyFactory {
      def childStarter() {
        context.actorOf(Props(newPilot(plane, autopilot, altimeter, controls)), pilotName)
        context.actorOf(Props(newCopilot(plane, autopilot, altimeter)), copilotName)
      }
    }), "Pilots")
    context.actorOf(Props(newFlightAttendant), leadAttendantName)
    Await.result(people ? WaitForStart, timeout.duration)
  }

}
