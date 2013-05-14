
import _root_.IsolatedLifeCycleSupervisor.{Started, WaitForStart}
import akka.actor.Actor

object IsolatedLifeCycleSupervisor {
  case object WaitForStart
  case object Started
}

trait IsolatedLifeCycleSupervisor extends Actor {

  def receive = {
    case WaitForStart => sender ! Started
    case m => throw new Exception(s"Don't call ${self.path.name} directly ($m}")
  }

  def childStarter()

  final override def preStart() {
    childStarter()
  }

  final override def postRestart(reason: Throwable) {
    // Don't call prestart which would be the default behaviour
  }

  final override def preRestart(reason: Throwable, message:Option[Any]) {
    // Don't stop children which would be the default behaviour
  }
}
