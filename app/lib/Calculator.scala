package lib

import play.api.Logger
import akka.agent.Agent
import akka.actor.ActorSystem

class Calculator(implicit sys: ActorSystem) {
  val hitReports = Agent(List[HitReport]())
  val listsOfStuff = Agent(ListsOfStuff())

  def calculate(cs: ClickStream) {
    Logger.info("Recalculating...")
    hitReports sendOff (_ => calcTopPaths(cs))
    listsOfStuff sendOff (_.diff(hitReports.get(), cs))
  }

  private def calcTopPaths(clickStream: ClickStream) = {
    val totalClicks = clickStream.allClicks.size
    val clicksPerPath = clickStream.allClicks.groupBy(_.path).map {
      case (k, v) => (k, v, v.size)
    }.toList
    val topTen = clicksPerPath.sortBy(_._3).reverse

    topTen map {
      case (url, hits, hitCount) =>
        HitReport(
          url = url,
          percent = hitCount.toDouble * 100 / totalClicks,
          hits = hitCount,
          hitsPerSec = (hitCount.toDouble / clickStream.secs) * Config.scalingFactor,
          events = hits.toList)
    }
  }
}

