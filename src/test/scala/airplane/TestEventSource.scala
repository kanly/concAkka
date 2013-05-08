package airplane

import akka.testkit.{TestActorRef, TestKit}
import akka.actor.{Actor, ActorSystem}
import org.scalatest.{BeforeAndAfterAll, WordSpec}
import org.scalatest.matchers.MustMatchers
import airplane.ProductionEventSource.{UnregisterListener, RegisterListener}

class TestEventSource extends Actor with ProductionEventSource{
  def receive = eventSourceReceiver
}

class EventSourceSpec extends TestKit(ActorSystem("EventSourceSpec")) with WordSpec with MustMatchers with BeforeAndAfterAll {
  override def afterAll(){system.shutdown()}

  "Event Source" should {
    "allow us to register a listener" in {
      val real = TestActorRef[TestEventSource].underlyingActor
      real.receive(RegisterListener(testActor))
      real.listeners must contain (testActor)
    }

    "allow us to unregister a listener" in {
      val real = TestActorRef[TestEventSource].underlyingActor
      real.receive(RegisterListener(testActor))
      real.receive(UnregisterListener(testActor))
      real.listeners.size must be (0)
    }

    "send the event to our test actor" in {
      val testA = TestActorRef[TestEventSource]
      testA ! RegisterListener(testActor)
      testA.underlyingActor.sendEvent("Fibonacci")
      expectMsg("Fibonacci")
    }
  }

}
