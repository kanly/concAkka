package airplane

import scala.concurrent.duration._
import akka.actor._
import scala.collection.JavaConverters._
import airplane.FlightAttendant.{Drink, GetDrink}
import airplane.Passenger.{UnfastenSeatbelts, FastenSeatbelts}

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.SupervisorStrategy.{Stop, Escalate, Resume}
import akka.actor.ActorKilledException
import airplane.FlightAttendant.GetDrink
import airplane.FlightAttendant.Drink
import airplane.PassengerSupervisor.{PassengerBroadcaster, GetPassengerBroadcaster}
import akka.routing.BroadcastRouter

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

object PassengerSupervisor {

  case object GetPassengerBroadcaster

  case class PassengerBroadcaster(broadCaster: ActorRef)

  def apply(callButton: ActorRef) = new PassengerSupervisor(callButton) with PassengerProvider
}

class PassengerSupervisor(callButton: ActorRef) extends Actor {
  this: PassengerProvider =>


  override val supervisorStrategy = OneForOneStrategy() {
    case _: ActorKilledException => Escalate
    case _: ActorInitializationException => Escalate
    case _ => Resume
  }

  case class GetChildren(forSomeone: ActorRef)

  case class Children(children: Iterable[ActorRef], childrenFor: ActorRef)

  override def preStart() {
    //IsolatedStopSupervisor
    context.actorOf(Props(new Actor {
      val config = context.system.settings.config
      override val supervisorStrategy = OneForOneStrategy() {
        case _: ActorKilledException => Escalate
        case _: ActorInitializationException => Escalate
        case _ => Stop
      }

      override def preStart() {
        import scala.collection.JavaConverters._
        import com.typesafe.config.ConfigList
        val passengers = config.getList("airplane.passengers")
        passengers.asScala.foreach {
          nameWithSeat =>
            val id = nameWithSeat.asInstanceOf[ConfigList].unwrapped(
            ).asScala.mkString("-").replaceAllLiterally(" ", "_")
            context.actorOf(Props(newPassenger(callButton)), id)
        }
      }

      override def receive = {
        case GetChildren(forSomeone: ActorRef) =>
          sender ! Children(context.children, forSomeone)
      }
    }), "PassengersSupervisor")
  }

  def noRouter: Receive = {
    case GetPassengerBroadcaster =>
      context.actorFor("PassengersSupervisor") ! GetChildren(sender)
    case Children(passengers, destinedFor) =>
      val router = context.actorOf(Props().withRouter(
        BroadcastRouter(passengers.toSeq)), "Passengers")
      destinedFor ! PassengerBroadcaster(router)
      context.become(withRouter(router))
  }
  def withRouter(router: ActorRef): Receive = {
    case GetPassengerBroadcaster =>
      sender ! PassengerBroadcaster(router)
  }
  def receive:Receive = noRouter

}

