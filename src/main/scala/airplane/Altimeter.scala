package airplane

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{ActorLogging, Actor}
import scala.concurrent.duration._

object Altimeter {

  case class RateChange(amount: Float)

  case class AltitudeUpdate(altitude: Double)

  def apply() = new Altimeter with ProductionEventSource
}

class Altimeter extends Actor with ActorLogging {
  this: EventSource =>

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
      sendEvent(AltitudeUpdate(altitude))

  }

  override def postStop() {
    ticker.cancel()
  }
}

trait AltimeterProvider {
  def newAltimeter: Actor = Altimeter()
}

