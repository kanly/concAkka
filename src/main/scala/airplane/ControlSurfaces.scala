package airplane

import akka.actor.{Actor, ActorRef}

object ControlSurfaces {

  case class StickBack(amount: Float)

  case class StickForward(amount: Float)

  case class StickRight(amount: Float)

  case class StickLeft(amount: Float)


}

class ControlSurfaces(altimeter: ActorRef, headingIndicator: ActorRef) extends Actor {

  import ControlSurfaces._
  import Altimeter._
  import HeadingIndicator._

  def receive = {
    case StickBack(amount) => altimeter ! RateChange(amount)
    case StickForward(amount) => altimeter ! RateChange(-amount)
    case StickRight(amount) => headingIndicator ! BankChange(amount)
    case StickLeft(amount) => headingIndicator ! BankChange(-amount)
  }
}

