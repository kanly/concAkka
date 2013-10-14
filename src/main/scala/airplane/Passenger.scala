package airplane

import scala.concurrent.duration._
import akka.actor.{ActorLogging, ActorRef, Actor}
import scala.collection.JavaConverters._
import airplane.FlightAttendant.{Drink, GetDrink}
import airplane.Passenger.{UnfastenSeatbelts, FastenSeatbelts}

import scala.concurrent.ExecutionContext.Implicits.global

class Passenger(callButton: ActorRef) extends Actor with ActorLogging {
  this: DrinkRequestProbability =>

  val r = scala.util.Random

  case object CallForDrink


  val Passenger.SeatAssignment(myname, _, _) = self.path.name.replaceAllLiterally("_", " ")

  val drinks = context.system.settings.config.getStringList("airplane.drinks").asScala.toIndexedSeq

  val scheduler = context.system.scheduler

  override def preStart() {
    self ! CallForDrink
  }

  def maybeSendDrinkRequest(): Unit = {
    if (r.nextFloat() > askThreshold) {
      val drinkname = drinks(r.nextInt(drinks.length))
      callButton ! GetDrink(drinkname)
    }
    scheduler.scheduleOnce(randomishTime(), self, CallForDrink)
  }

  def receive = {
    case CallForDrink =>
      maybeSendDrinkRequest()
    case Drink(drinkname) =>
      log.info("{} received a {} - Yum", myname, drinkname)
    case FastenSeatbelts =>
      log.info("{} fastening seatbelt", myname)
    case UnfastenSeatbelts =>
      log.info("{} UNfastening seatbelt", myname)
  }


}

object Passenger {

  case object FastenSeatbelts

  case object UnfastenSeatbelts

  val SeatAssignment = """([\w\s_]+)-(\d+)-([A-Z])""".r
}


trait DrinkRequestProbability {
  val askThreshold = 0.9f
  val requestMin = 20.minutes
  val requestUpper = 30.minutes

  def randomishTime(): FiniteDuration = {
    requestMin + scala.util.Random.nextInt(
      requestUpper.toMillis.toInt).millis
  }
}

trait PassengerProvider {
  def newPassenger(callButton: ActorRef): Actor =
    new Passenger(callButton) with DrinkRequestProbability
}

