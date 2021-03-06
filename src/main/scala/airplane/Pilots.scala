package airplane

import akka.actor.{Terminated, ActorRef, Actor}
import airplane.Plane.{Copilot, WhoIsCopilot, Controls, GiveMeControl}


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

class AutoPilot(plane: ActorRef) extends Actor {

  import Pilots._

  var controls: ActorRef = context.system.deadLetters
  var copilot: ActorRef = context.system.deadLetters

  def receive = {
    case ReadyToGo =>
      plane ! WhoIsCopilot
    case Copilot(pilot) =>
      copilot = pilot
      context.watch(copilot)
    case Terminated(_) =>
      plane ! GiveMeControl
  }
}

trait PilotProvider {
  def newPilot(plane: ActorRef, autopilot: ActorRef, altimeter: ActorRef, headingControl: ActorRef, controls: ActorRef): Actor =
    new Pilot(plane, autopilot, altimeter, controls)

  def newCopilot(plane: ActorRef, autopilot: ActorRef, altimeter: ActorRef, headingControl: ActorRef): Actor =
    new CoPilot(plane, autopilot, altimeter)

  def newAutopilot(plane: ActorRef): Actor = new AutoPilot(plane)
}

