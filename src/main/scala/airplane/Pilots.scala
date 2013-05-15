package airplane

import akka.actor.{Terminated, ActorRef, Actor}
import airplane.Plane.{Controls, GiveMeControl}


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

