package lib

import akka.actor.{ActorLogging, Actor}
import play.api.Logger
import akka.agent.Agent

class CalculatorAgent {
  val hitReports = Agent[List[HitReport]](List())
  val listsOfStuff = Agent[ListsOfStuff](ListsOfStuff())

  def calculate(cs: ClickStream) {
//    log.info("Recalculating...")
    hitReports sendOff (_ => calcTopPaths(cs))
    listsOfStuff sendOff (_.diff(hitReports.get(), cs))
  }

  def get() = (hitReports.get(), listsOfStuff.get())

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


class Calculator extends Actor with ActorLogging {
  protected def receive = {
    case cs: ClickStream =>
      Backend.calculatorAgent.calculate(cs)
  }
}

