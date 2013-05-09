package airplane

import akka.actor._
import scala.concurrent.duration._
import airplane.Altimeter.AltitudeUpdate
import airplane.Pilots.ReadyToGo

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

  import Plane._
  import ProductionEventSource._

  val altimeter = context.actorOf(Props[Altimeter])
  val controls = context.actorOf(Props(new ControlSurfaces(altimeter)))
  val config = context.system.settings.config
  val pilot = context.actorOf(Props[Pilot], config.getString("airplane.flightcrew.pilotName"))
  val copilot = context.actorOf(Props[CoPilot], config.getString("airplane.flightcrew.copilotName"))
  val autopilot = context.actorOf(Props[AutoPilot], "AutoPilot")
  val flightAttendant = context.actorOf(Props(LeadFlightAttendant()), config.getString("airplane.flightcrew.leadAttendantName"))


  def receive = {
    case AltitudeUpdate(altitude) =>
      log.info(s"Altitude is now: $altitude")
    case GiveMeControl =>
      log.info("Plane giving control")
      sender ! Controls(controls)
  }

  override def preStart() {
    altimeter ! RegisterListener(self)
    List(pilot, copilot, autopilot) foreach {
      _ ! ReadyToGo
    }
  }
}