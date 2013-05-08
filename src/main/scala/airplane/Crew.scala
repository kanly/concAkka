package airplane

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import scala.concurrent.duration._
import akka.actor.Actor
import airplane.FlightAttendant.{Drink, GetDrink}

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
