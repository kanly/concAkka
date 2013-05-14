package airplane

import utils.IsolatedLifeCycleSupervisor
import IsolatedLifeCycleSupervisor.WaitForStart
import akka.actor._
import scala.concurrent.duration._
import airplane.Altimeter.AltitudeUpdate
import scala.concurrent.Await
import akka.util.Timeout

//imports the default global execution context, see http://docs.scala-lang.org/overviews/core/futures.html

import scala.concurrent.ExecutionContext.Implicits.global

object Altimeter {

  case class RateChange(amount: Float)

  case class AltitudeUpdate(altitude: Double)

  def apply() = new Altimeter with ProductionEventSource
}

class Altimeter extends Actor with ActorLogging {
  this: EventSource =>

  import airplane.Altimeter._


  val ceiling = 43000
  val maxRateOfClimb = 5000

  var rateOfClimb: Float = 0.0f
  var altitude: Double = 0.0

  var lastTick = System.currentTimeMillis
  val ticker = context.system.scheduler.schedule(100 millis, 100 millis, self, Tick)

  case object Tick

  def receive = eventSourceReceiver orElse altimeterReceive

  // the Receive return type identify a partial applied function in order to compose receive method as above
  def altimeterReceive: Receive = {
    case RateChange(amount) =>
      rateOfClimb = amount.min(1.0f).max(-1.0f) * maxRateOfClimb
      log.info(s"Altimeter changed rate of climb to $rateOfClimb.")
    case Tick =>
      val tick = System.currentTimeMillis
      altitude = altitude + ((tick - lastTick) / 1.minute.toMillis.toFloat) * rateOfClimb
      lastTick = tick
      sendEvent(AltitudeUpdate(altitude))

  }

  override def postStop() {
    ticker.cancel()
  }
}

trait AltimeterProvider {
  def newAltimeter: Actor = Altimeter()
}

object ControlSurfaces {

  case class StickBack(amount: Float)

  case class StickForward(amount: Float)

}

class ControlSurfaces(altimeter: ActorRef) extends Actor {

  import ControlSurfaces._
  import Altimeter._

  def receive = {
    case StickBack(amount) => altimeter ! RateChange(amount)
    case StickForward(amount) => altimeter ! RateChange(-amount)
  }
}

object Plane {

  case object GiveMeControl

  case class Controls(controlSurfaces: ActorRef)

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
        context.actorOf(Props(newAutopilot), "AutoPilot")
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