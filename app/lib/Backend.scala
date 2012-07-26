package lib

import java.util.concurrent.TimeUnit
import com.gu.openplatform.contentapi.model.Content
import org.joda.time.{Duration, DateTime}
import akka.actor._
import akka.util.duration._
import akka.dispatch.{Await, Future}
import akka.util.Timeout
import akka.pattern.ask
import concurrent.ops
import ops._

object Backend {
  implicit val system = ActorSystem("liveDashboard")
  val listener = system.actorOf(Props[ClickStreamActor], name = "clickStreamListener")
  val calculator = system.actorOf(Props[Calculator], name = "calculator")
  val searchTerms = system.actorOf(Props[SearchTermActor], name = "searchTermProcessor")

  val latestContent = new LatestContent

  val ukFrontLinkTracker = new LinkTracker("http://www.guardian.co.uk")
  val usFrontLinkTracker = new LinkTracker("http://www.guardiannews.com")

  val mqReader = new MqReader(listener :: searchTerms :: Nil)

  def start() {
  }

  def stop() {
  }

  // So this is a bad way to do this, should use akka Agents instead (which can read
  // without sending a message.)

  implicit val timeout = Timeout(5 seconds)

  def currentStats = Await.result( (calculator ? Calculator.GetStats).mapTo[(List[HitReport], ListsOfStuff)], 5 seconds)

  def currentLists = currentStats._2

  def currentHits = currentStats._1

  def liveSearchTermsFuture = (searchTerms ? SearchTermActor.GetSearchTerms).mapTo[List[GuSearchTerm]]
  def liveSearchTerms = Await.result(liveSearchTermsFuture, timeout.duration)

  // this one uses an agent: this is the model that others should follow
  // (agents are multi non-blocking read, single update)
  def publishedContent = latestContent.latest()

  def minutesOfData = {
    val currentData = currentLists
    new Duration(currentData.firstUpdated, currentData.lastUpdated).getStandardMinutes
  }
}