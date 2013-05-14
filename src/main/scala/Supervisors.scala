
import _root_.IsolatedLifeCycleSupervisor.{Started, WaitForStart}
import akka.actor._
import akka.actor.AllForOneStrategy
import akka.actor.OneForOneStrategy
import scala.concurrent.duration.Duration
import akka.actor.SupervisorStrategy.{Stop, Resume, Escalate, Decider}


trait SupervisionStrategyFactory {
  def makeStrategy(maxNrRetries: Int,
                   withinTimeRange: Duration)(decider: Decider): SupervisorStrategy
}

trait OneForOneStrategyFactory extends SupervisionStrategyFactory {
  def makeStrategy(maxNrRetries: Int,
                   withinTimeRange: Duration)(decider: Decider): SupervisorStrategy =
    OneForOneStrategy(maxNrRetries, withinTimeRange)(decider)
}

trait AllForOneStrategyFactory extends SupervisionStrategyFactory {
  def makeStrategy(maxNrRetries: Int,
                   withinTimeRange: Duration)(decider: Decider): SupervisorStrategy =
    AllForOneStrategy(maxNrRetries, withinTimeRange)(decider)
}


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

  final override def preRestart(reason: Throwable, message: Option[Any]) {
    // Don't stop children which would be the default behaviour
  }
}

abstract class IsolatedResumeSupervisor(maxNrRetries: Int = -1, withinTimeRange: Duration = Duration.Inf)
  extends IsolatedLifeCycleSupervisor {

  this: SupervisionStrategyFactory =>

  override def supervisorStrategy = makeStrategy(maxNrRetries,withinTimeRange){
    case _: ActorInitializationException => Stop
    case _: ActorKilledException => Stop
    case _: Exception => Resume
    case _ => Escalate
  }
}
