package airplane

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import akka.actor.{Cancellable, Props, ActorRef, Actor}
import scala.concurrent.duration._
import airplane.FlightAttendant._
import airplane.LeadFlightAttendant.GetFlightAttendant
import airplane.FlightAttendant.Assist
import airplane.FlightAttendant.GetDrink
import airplane.FlightAttendant.Drink
import scala.Some
import airplane.LeadFlightAttendant.Attendant

trait AttendantResponsiveness {
  val maxResponseTimeMS: Int

  def responseDuration = Random.nextInt(maxResponseTimeMS).millis
}

object FlightAttendant {

  case class GetDrink(drinkName: String)

  case class Drink(drinkName: String)

  case class Assist(passenger: ActorRef)

  case object Busy_?

  case object Yes

  case object No

  def apply() = new FlightAttendant with AttendantResponsiveness {
    val maxResponseTimeMS = 300000
  }

}

class FlightAttendant extends Actor {
  this: AttendantResponsiveness =>

  case class DeliverDrink(drink: Drink)

  var pendingDelivery: Option[Cancellable] = None

  def scheduleDelivery(drinkname: String): Cancellable = {
    context.system.scheduler.scheduleOnce(responseDuration, self, DeliverDrink(Drink(drinkname)))
  }

  def assistInjuredPassenger: Receive = {
    case Assist(passenger) =>

      pendingDelivery foreach {
        _.cancel()
      }
      pendingDelivery = None
      passenger ! Drink("Magic Healing Potion")
  }

  def handleDrinkRequests: Receive = {
    case GetDrink(drinkname) =>
      pendingDelivery = Some(scheduleDelivery(drinkname))

      context.become(assistInjuredPassenger orElse
        handleSpecificPerson(sender))
    case Busy_? =>
      sender ! No
  }

  def handleSpecificPerson(person: ActorRef): Receive = {
    case GetDrink(drinkname) if sender == person =>
      pendingDelivery foreach {
        _.cancel()
      }
      pendingDelivery = Some(scheduleDelivery(drinkname))

    case DeliverDrink(drink) =>
      person ! drink
      pendingDelivery = None

      context.become(assistInjuredPassenger orElse handleDrinkRequests)

    case m: GetDrink =>
      context.parent forward m
    case Busy_? =>
      sender ! Yes
  }

  def receive = assistInjuredPassenger orElse handleDrinkRequests

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

