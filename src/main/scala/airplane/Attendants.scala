package airplane

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import akka.actor.{Props, ActorRef, Actor}
import scala.concurrent.duration._
import airplane.FlightAttendant.{GetDrink, Drink}
import airplane.LeadFlightAttendant.{GetFlightAttendant, Attendant}

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

