package airplane

import org.scalatest.{BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers
import akka.testkit.{TestActorRef, TestKit, ImplicitSender}
import akka.actor.{Props, ActorSystem}
import java.util.concurrent.{TimeUnit, CountDownLatch}
import airplane.Altimeter.{AltitudeUpdate, RateChange}


object EventSourceSpy {
  val latch = new CountDownLatch(1)
}

trait EventSourceSpy extends EventSource {
  def sendEvent[T](event: T) {
    EventSourceSpy.latch.countDown()
  }

  def eventSourceReceiver = {
    case "" =>
  }
}

class AltimeterSpec extends TestKit(ActorSystem("AltimeterSpec")) with ImplicitSender with WordSpec with MustMatchers with BeforeAndAfterAll {
  override def afterAll() {
    system.shutdown()
  }

 // val slicedAltimeter = new Altimeter with EventSourceSpy

  def actor() = {
    val actorRef = TestActorRef[Altimeter](Props(new Altimeter with EventSourceSpy))
    (actorRef, actorRef.underlyingActor)
  }

  "Altimeter" should {
    "record rate of climb change" in {
      val (_, real) = actor()
      real.receive(RateChange(1f))
      real.rateOfClimb must be(real.maxRateOfClimb)
    }

    "keep rate of climb within bounds" in {
      val (_, real) = actor()
      real.receive(RateChange(2f))
      real.rateOfClimb must be(real.maxRateOfClimb)
    }

    "calculate altitude changes" in {
      val ref = system.actorOf(Props(Altimeter()))
      ref ! ProductionEventSource.RegisterListener(testActor)
      ref ! RateChange(1f)
      fishForMessage() {
        case AltitudeUpdate(altitude) if (altitude) ==0f => false
        case AltitudeUpdate(_) => true
      }
    }
    "send events" in {
      val (ref, _) = actor()
      EventSourceSpy.latch.await(1, TimeUnit.SECONDS) must be (true)
    }
  }
}
