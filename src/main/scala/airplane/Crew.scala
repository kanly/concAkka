package airplane

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import scala.concurrent.duration._
import akka.actor.{Terminated, Props, ActorRef, Actor}
import airplane.FlightAttendant.{Drink, GetDrink}
import airplane.LeadFlightAttendant.{GetFlightAttendant, Attendant}
import airplane.Plane.{Controls, GiveMeControl}

trait AttendantResponsiveness {
  val maxResponseTimeMS: Int

  def responseDuration = Random.nextInt(maxResponseTimeMS).millis
}

object FlightAttendant {

  case class GetDrink(drinkName: String)

  case class Drink(drinkName: String)

  def apply() = new FlightAttendant with AttendantResponsiveness {
    val maxResponseTimeMS = 300000
  }

}

class FlightAttendant extends Actor {
  this: AttendantResponsiveness =>

  def receive = {
    case GetDrink(drinkName) => context.system.scheduler.scheduleOnce(responseDuration, sender, Drink(drinkName))
  }
}

trait AttendantCreationPolicy {
  val numberOfAttendants = 8

  def createAttendant = FlightAttendant()
}

trait LeadFlightAttendantProvider {
  def newFlightAttendant = LeadFlightAttendant()
}

object LeadFlightAttendant {

  case object GetFlightAttendant

  case class Attendant(a: ActorRef)

  def apply() = new LeadFlightAttendant with AttendantCreationPolicy
}

class LeadFlightAttendant extends Actor {
  this: AttendantCreationPolicy =>

  override def preStart() {
    import scala.collection.JavaConverters._

    val attendantNames = context.system.settings.config.getStringList("airplane.flightcrew.attendantNames").asScala
    attendantNames take numberOfAttendants foreach {
      i => context.actorOf(Props(createAttendant), i)
    }
  }

  def randomAttendant(): ActorRef = {
    context.children.take(
      scala.util.Random.nextInt(numberOfAttendants) + 1).last
  }

  def receive = {
    case GetFlightAttendant =>
      sender ! Attendant(randomAttendant())
    case m =>
      randomAttendant() forward m
  }
}

object Pilots {

  case object ReadyToGo

  case object RelinquishControl

}

class Pilot(plane: ActorRef, autopilot: ActorRef, altimeter: ActorRef, var controls: ActorRef) extends Actor {

  import Pilots._
  import Plane._

  var copilot: ActorRef = context.system.deadLetters
  val copilotName = context.system.settings.config.getString("airplane.flightcrew.copilotName")

  def receive = {
    case ReadyToGo =>
      copilot = context.actorFor("../" + copilotName)
    case Controls(controlSurfaces) =>
      controls = controlSurfaces
  }
}

class CoPilot(plane: ActorRef, autopilot: ActorRef, altimeter: ActorRef) extends Actor {

  import Pilots._

  var pilot: ActorRef = context.system.deadLetters
  val pilotName = context.system.settings.config.getString("airplane.flightcrew.pilotName")
  var controls = context.system.deadLetters

  def receive = {
    case ReadyToGo =>
      pilot = context.actorFor("../" + pilotName)
      context.watch(pilot)
    case Terminated(_) =>
      plane ! GiveMeControl
    case Controls(controlSurfaces) =>
      controls = controlSurfaces
  }
}

class AutoPilot extends Actor {

  import Pilots._

  var controls: ActorRef = context.system.deadLetters
  var pilot: ActorRef = context.system.deadLetters
  var copilot: ActorRef = context.system.deadLetters
  val pilotName = context.system.settings.config.getString("airplane.flightcrew.pilotName")
  val copilotName = context.system.settings.config.getString("airplane.flightcrew.copilotName")

  def receive = {
    case ReadyToGo =>
      pilot = context.actorFor("../" + pilotName)
      copilot = context.actorFor("../" + copilotName)
  }
}

trait PilotProvider {
  def newPilot(plane: ActorRef, autopilot: ActorRef, altimeter: ActorRef, controls: ActorRef): Actor =
    new Pilot(plane, autopilot, altimeter, controls)

  def newCopilot(plane: ActorRef, autopilot: ActorRef, altimeter: ActorRef): Actor =
    new CoPilot(plane, autopilot, altimeter)

  def newAutopilot: Actor = new AutoPilot
}

