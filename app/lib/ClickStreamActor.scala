package lib

import akka.actor._
import collection.GenSeq
import org.scala_tools.time.Imports._
import akka.agent.Agent


// it's very very important that this class is totally immutable!
case class ClickStream(allClicks: GenSeq[Event], lastUpdated: DateTime, firstUpdated: DateTime) {
  lazy val userClicks = allClicks

  def +(e: Event) = copy(allClicks = e +: allClicks, lastUpdated = e.dt)

  def removeEventsBefore(dt: DateTime) = copy(
    allClicks = allClicks.filterNot(_.dt < dt),
    firstUpdated = if (firstUpdated > dt) firstUpdated else dt
  )

  def ageMs = DateTime.now.millis - lastUpdated.millis

  lazy val timePeriodMillis = lastUpdated.millis - firstUpdated.millis
  lazy val secs = timePeriodMillis / 1000
}


class ClickStreamActor(retentionPeriod: Long) extends Actor with ActorLogging {
  var clickStream = ClickStream(Nil.par, DateTime.now, DateTime.now)

  import ClickStreamActor._

  protected def receive = {
    case e: Event => {
      Backend.clickStreamAgent.add(e)
    }

    case TruncateClickStream => {
      Backend.clickStreamAgent.truncate()
    }

    case SendClickStreamTo(actor) => actor ! Backend.clickStreamAgent.get()

  }
}
class ClickStreamAgent(retentionPeriod: Long)(implicit sys: ActorSystem) {
  val clickStream = Agent[ClickStream](ClickStream(Nil.par, DateTime.now, DateTime.now))

  def add(e: Event) = clickStream send (_ + (e))
  def truncate() = clickStream sendOff(_.removeEventsBefore(DateTime.now - retentionPeriod))
  def get() = clickStream.get()
}
object ClickStreamActor {
  sealed trait Messages
  case object TruncateClickStream extends Messages
  case class SendClickStreamTo(actor: ActorRef) extends Messages

}









