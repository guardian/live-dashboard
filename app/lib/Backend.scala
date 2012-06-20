package lib

import org.joda.time.Duration
import akka.actor._
import akka.util.duration._

object Backend {
  implicit val system = ActorSystem("liveDashboard")

  private val latestContent = new LatestContent

  val ukFrontLinkTracker = new LinkTracker("http://www.guardian.co.uk")
  val usFrontLinkTracker = new LinkTracker("http://www.guardiannews.com")

  private val calculator = new Calculator()
  private val searchTerms = new SearchTermAgent()

  def start() {
    system.scheduler.schedule(5 seconds, 5 seconds) { calculator.calculate() }
    system.scheduler.schedule(5 seconds, 30 seconds) { latestContent.refresh() }
    system.scheduler.schedule(1 seconds, 20 seconds) { ukFrontLinkTracker.refresh() }
    system.scheduler.schedule(20 seconds, 60 seconds) { usFrontLinkTracker.refresh() }

  }

  def stop() {
    system.shutdown()
  }

  def currentLists = calculator.listsOfStuff()
  def currentHits = calculator.hitReports()

  def lastUpdated = calculator.lastUpdated()
  def updateWindowStart = calculator.updateWindowStart

  def totalHits = calculator.totalHits()

  def liveSearchTerms = searchTerms()
  def publishedContent = latestContent.latest()

  def viewsPerSecond = currentLists.hitsPerSecondOption.getOrElse(0L)

  def minutesOfData = {
    val currentData = currentLists
    new Duration(currentData.firstUpdated, currentData.lastUpdated).getStandardMinutes
  }
}