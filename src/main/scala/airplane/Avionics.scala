package airplane

import akka.actor._
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.Await

//imports the default global execution context, see http://docs.scala-lang.org/overviews/core/futures.html

import scala.concurrent.ExecutionContext.Implicits.global

object Altimeter {

  case class RateChange(amount: Float)
  case class AltitudeUpdate(altitude: Double)

}

class Altimeter extends Actor with ActorLogging with EventSource {

  import airplane.Altimeter._


  val ceiling = 43000
  val maxRateOfClimb = 5000

  var rateOfClimb: Float = 0.0f
  var altitude: Double = 0.0

  var lastTick = System.currentTimeMillis
  val ticker = context.system.scheduler.schedule(100 millis, 100 millis, self, Tick)

  case object Tick

  def receive = eventSourceReceiver orElse altimeterReceive

  // the Receive return type identify a partial applied function in order to compose receive method as above
  def altimeterReceive: Receive = {
    case RateChange(amount) =>
      rateOfClimb = amount.min(1.0f).max(-1.0f) * maxRateOfClimb
      log.info(s"Altimeter changed rate of climb to $rateOfClimb.")
    case Tick =>
      val tick = System.currentTimeMillis
      altitude = altitude + ((tick - lastTick) / 1.minute.toMillis.toFloat) * rateOfClimb
      lastTick = tick

  }

  override def postStop() {
    ticker.cancel()
  }
}


object ControlSurfaces {

  case class StickBack(amount: Float)

  case class StickForward(amount: Float)

}

class ControlSurfaces(altimeter: ActorRef) extends Actor {

  import ControlSurfaces._
  import Altimeter._

  def receive = {
    case StickBack(amount) => altimeter ! RateChange(amount)
    case StickForward(amount) => altimeter ! RateChange(-amount)
  }
}

object Plane {

  case object GiveMeControl

}

class Plane extends Actor with ActorLogging {

  import Plane._

  val altimeter = context.actorOf(Props[Altimeter])
  val controls = context.actorOf(Props(new ControlSurfaces(altimeter)))


  def receive = {
    case GiveMeControl =>
      log.info("Plane giving control")
      sender ! controls
  }
}


object Avionics {


  //needed for ? (ask method)

  import akka.pattern.ask


  // needed for ask's future timeout below
  implicit val timeout = Timeout(5.seconds)
  val system = ActorSystem("PlaneSimulation")
  val plane = system.actorOf(Props[Plane], "Plane")

  def main(args: Array[String]) {
    // Grab the controls
    val control = Await.result(
      (plane ? Plane.GiveMeControl).mapTo[ActorRef], 5.seconds)
    // Takeoff!
    system.scheduler.scheduleOnce(200.millis) {
      control ! ControlSurfaces.StickBack(1f)
    }
    // Level out
    system.scheduler.scheduleOnce(1.seconds) {
      control ! ControlSurfaces.StickBack(0f)
    }
    // Climb
    system.scheduler.scheduleOnce(3.seconds) {
      control ! ControlSurfaces.StickBack(0.5f)
    }
    // Level out
    system.scheduler.scheduleOnce(4.seconds) {
      control ! ControlSurfaces.StickBack(0f)
    }
    // Shut down
    system.scheduler.scheduleOnce(5.seconds) {
      system.shutdown()
    }
  }

}


