package airplane

import akka.testkit.TestActorRef
import akka.actor.Actor
import org.scalatest.{ParallelTestExecution, WordSpec}
import org.scalatest.matchers.MustMatchers
import airplane.ProductionEventSource.{UnregisterListener, RegisterListener}
import helper.ActorSys

class TestEventSource extends Actor with ProductionEventSource {
  def receive = eventSourceReceiver
}

class EventSourceSpec extends WordSpec with MustMatchers with ParallelTestExecution {


  "Event Source" should {
    "allow us to register a listener" in new ActorSys {
      val real = TestActorRef[TestEventSource].underlyingActor
      real.receive(RegisterListener(testActor))
      real.listeners must contain(testActor)
    }

    "allow us to unregister a listener" in new ActorSys {
      val real = TestActorRef[TestEventSource].underlyingActor
      real.receive(RegisterListener(testActor))
      real.receive(UnregisterListener(testActor))
      real.listeners.size must be(0)
    }

    "send the event to our test actor" in new ActorSys {
      val testA = TestActorRef[TestEventSource]
      testA ! RegisterListener(testActor)
      testA.underlyingActor.sendEvent("Fibonacci")
      expectMsg("Fibonacci")
    }
  }

}
