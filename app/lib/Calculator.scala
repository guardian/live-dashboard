package lib

import akka.actor.Actor
import play.api.Logger




class Calculator extends Actor {
  val log = Logger(getClass)

  // this is a raw list of all the hits we've seen, rolled up by path
  private var currentTopPaths: List[HitReport] = Nil

  // this is all sorts of sublists suitable for ui display (based on the above)
  private var listsOfStuff: ListsOfStuff = ListsOfStuff()

  import Calculator._
  protected def receive = {
    case cs: ClickStream =>
      log.info("Recalculating...")
      currentTopPaths = calcTopPaths(cs)
      listsOfStuff = listsOfStuff.diff(currentTopPaths, cs)
      log.info("Done")

    case GetStats => sender ! (currentTopPaths, listsOfStuff)
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

object Calculator {
  sealed trait Messages
  case object GetStats extends Messages
}

