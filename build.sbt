name := "concAkka"

version := "1.0"

scalaVersion := "2.10.0"

libraryDependencies ++= Seq( 
	"com.typesafe.akka" % "akka-actor_2.10" % "2.1.2",
	"org.scalatest" % "scalatest_2.10" % "1.9.1" % "test",
	"com.typesafe.akka" % "akka-testkit_2.10" % "2.1.2"
)

