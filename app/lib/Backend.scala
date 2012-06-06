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

  val latestContent = new LatestContent

  val ukFrontLinkTracker = new LinkTracker("http://www.guardian.co.uk")
  val usFrontLinkTracker = new LinkTracker("http://www.guardiannews.com")

  val clickStream = new ClickStreamAgent(Config.eventHorizon)
  val calculator = new Calculator()
  val searchTerms = new SearchTermAgent()

  val eventProcessors = clickStream :: searchTerms :: Nil

  val mqReader = new MqReader(eventProcessors)

  def start() {
    system.scheduler.schedule(1 minute, 1 minute) { clickStream.truncate() }
    system.scheduler.schedule(5 seconds, 5 seconds) { calculator.calculate(clickStream()) }
    system.scheduler.schedule(5 seconds, 30 seconds) { latestContent.refresh() }
    system.scheduler.schedule(1 seconds, 20 seconds) { ukFrontLinkTracker.refresh() }
    system.scheduler.schedule(20 seconds, 60 seconds) { usFrontLinkTracker.refresh() }

    if (Config.listenToMessageQueue) {
      spawn {
        mqReader.start()
      }
    }
  }

  def stop() {
    if (Config.listenToMessageQueue) {
      mqReader.stop()
    }
    system.shutdown()
  }

  implicit val timeout = Timeout(5 seconds)

  def currentStats = calculator.get()

  def currentLists = currentStats._2

  def currentHits = currentStats._1

  def userAgents = clickStream().allClicks
    .groupBy(_.userAgent)
    .map { case (agent, list) => agent -> list.size }
    .toList
    .sortBy { case (agent, count) => -count }

  def liveSearchTerms = searchTerms()

  def publishedContent = latestContent.latest()


  def viewsPerSecond = currentLists.hitsPerSecondOption.getOrElse(0L)

  def minutesOfData = {
    val currentData = currentLists
    new Duration(currentData.firstUpdated, currentData.lastUpdated).getStandardMinutes
  }
}