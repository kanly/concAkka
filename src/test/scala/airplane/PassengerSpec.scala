package airplane

import scala.concurrent.duration._
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import akka.actor.{Props, ActorRef, ActorSystem}
import org.scalatest.WordSpec
import akka.event.Logging.Info
import airplane.Passenger.FastenSeatbelts
import org.scalatest.matchers.MustMatchers

class PassengerSpec extends TestKit(ActorSystem()) with ImplicitSender with WordSpec with MustMatchers {

  var seatNumber = 9

  def newPassenger(): ActorRef = {
    seatNumber += 1
    system.actorOf(Props(new Passenger(testActor) with TestDrinkRequestProbability), s"Pat_Metheny-$seatNumber-B")
  }

  "Passengers" should {
    "fasten seatbelts when asked" in {
      val a = newPassenger()
      val p = TestProbe()
      system.eventStream.subscribe(p.ref, classOf[Info])
      a ! FastenSeatbelts
      p.expectMsgPF() {
        case Info(_, _, m) =>
          m.toString must include("fastening seatbelt")
      }
    }

  }


}


trait TestDrinkRequestProbability extends DrinkRequestProbability {
  override val askThreshold = 0f
  override val requestMin = 0.milliseconds
  override val requestUpper = 2.milliseconds

}
