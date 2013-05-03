package actors

import akka.actor.{ActorSystem, Props, Actor}

class BadShakespearean extends Actor {
  def receive = {
    case "Good Morning" => println("Him: Forsooth 'tis the 'morn, but mourneth for thou doest I do!")
    case "You're terrible" => println("Him: Yup!")
  }
}

object BadShakespeareanMain extends App {
  val system = ActorSystem("BadShakespearean")
  val actor = system.actorOf(Props[BadShakespearean])

  def send(msg: String) {
    println("Me:" + msg)
    actor ! msg
    Thread.sleep(100)
  }

  send("Good Morning")
  send("You're terrible")
  system.shutdown()

}
