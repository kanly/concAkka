package helper

import akka.testkit.{ImplicitSender, TestKit}
import akka.actor.ActorSystem
import scala.util.Random

class ActorSys(name: String) extends TestKit(ActorSystem(name)) with ImplicitSender with DelayedInit {
  def this() = this(s"TestSystem${Random.nextInt(30)}")

  def shutdown() {
    system.shutdown()
  }

  def delayedInit(x: => Unit) {
    try {
      x
    } finally {
      shutdown()
    }
  }
}
