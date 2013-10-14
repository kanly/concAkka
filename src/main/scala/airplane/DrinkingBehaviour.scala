package airplane

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, ActorRef}
import scala.concurrent.duration._


object DrinkingBehaviour {

  case class LevelChanged(level: Float)

  case object FeelingSober

  case object FeelingTipsy

  case object FeelingLikeZaphod

  def apply(drinker: ActorRef) = new DrinkingBehaviour(drinker)
    with DrinkingResolution

}

class DrinkingBehaviour(drinker: ActorRef) extends Actor {
  this: DrinkingResolution =>

  import DrinkingBehaviour._

  var currentLevel = 0f
  val scheduler = context.system.scheduler
  val sobering = scheduler.schedule(initialSobering, soberingInterval, self, LevelChanged(-0.0001f))

  override def postStop() {
    sobering.cancel()
  }

  override def preStart() {
    drink()
  }

  def drink() = scheduler.scheduleOnce(drinkInterval(), self, LevelChanged(0.005f))

  def receive = {
    case LevelChanged(amount) =>
      currentLevel = (currentLevel + amount).max(0f)
      drinker ! drunkiness(currentLevel)
  }

  def drunkiness(currentLevel: Float) =
    if (currentLevel <= 0.01) {
      drink()
      FeelingSober
    } else if (currentLevel <= 0.03) {
      drink()
      FeelingTipsy
    }
    else FeelingLikeZaphod
}

trait DrinkingResolution {

  import scala.util.Random

  def initialSobering = 1.second

  def soberingInterval = 1.second

  def drinkInterval() = Random.nextInt(300).seconds
}
