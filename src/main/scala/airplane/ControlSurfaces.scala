package airplane

import akka.actor.{Actor, ActorRef}

object ControlSurfaces {

  case class StickBack(amount: Float)

  case class StickForward(amount: Float)

  case class StickRight(amount: Float)

  case class StickLeft(amount: Float)

  case class HasControl(pilot: ActorRef)


}

class ControlSurfaces(plane: ActorRef, altimeter: ActorRef, headingIndicator: ActorRef) extends Actor {

  import ControlSurfaces._
  import Altimeter._
  import HeadingIndicator._


  def receive = controlledBy(context.system.deadLetters)

  def controlledBy(somePilot: ActorRef): Receive = {
    case StickBack(amount) if (sender == somePilot) => altimeter ! RateChange(amount)
    case StickForward(amount) if (sender == somePilot) => altimeter ! RateChange(-amount)
    case StickRight(amount) if (sender == somePilot) => headingIndicator ! BankChange(amount)
    case StickLeft(amount) if (sender == somePilot) => headingIndicator ! BankChange(-amount)
    case HasControl(pilot) if (sender == plane) => context.become(controlledBy(pilot))
  }
}

