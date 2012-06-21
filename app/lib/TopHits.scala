package lib

import org.scala_tools.time.Imports._
import controllers.routes
import org.elasticsearch.index.query.QueryBuilders

sealed abstract class Movement { def img: Option[String] }
case class Unchanged() extends Movement { val img = None }
case class NewEntry() extends Movement { val img = Some("new") }
case class Up() extends Movement { val img = Some("up") }
case class Down() extends Movement { val img = Some("down") }

case class Referrer(url: String, count: Long)

case class HitReport(url: String, percent: Double, hits: Int, hitsPerSec: Double,
    topReferrers: Iterable[Referrer], movement: Movement = Unchanged()) {
  def summary = "%s %.1f%% (%d hits)" format (url, percent, hits)

  lazy val referrersWithCounts = topReferrers.map {
    case Referrer(host, count) =>
      host -> count
  }

  lazy val referrerPercents: List[(String, Double)] = topReferrers.map {
    case Referrer(host, count) =>
      host -> (count * 100.0 / hits)
  }.toList

  lazy val id = url.replace("/", "")

  lazy val fullUrl = url

  lazy val detailUrl = routes.Application.detail(id)

  lazy val cssClass = if (hitsPerSec >= 1.0) "high" else ""

  lazy val tidyHitsPerSec = tidy("%.1f".format(hitsPerSec))

  private def tidy(s: String) = s match {
    case "0.0" => "<0.1"
    case other => other
  }
}

case class ListsOfStuff(
    all: TopHits = TopHits(),
    everything: TopHits = TopHits(),
    content: TopHits = TopHits(),
    other: TopHits = TopHits(),
    lastUpdated: DateTime = DateTime.now,
    firstUpdated: DateTime = DateTime.now,
    totalHits: Long = 0,
    clickStreamSecs: Long = 0) {
  import ListsOfStuff._

  private val fmt = "d MMM yyyy h:mm:ss a"

  lazy val ageString = "%s to %s" format (
    firstUpdated.toString(fmt),
    lastUpdated.toString(fmt)
  )

  lazy val hitsScaledToAllServers = totalHits * Config.scalingFactor
  lazy val hitsPerSecond = {
    val now = DateTime.now
    val since = now.minusMillis(Config.eventHorizon.toInt)
    val results = ElasticSearch.client.prepareSearch(ElasticSearch.indexNameForDate(now))
      .setSize(0)
      .setQuery(QueryBuilders.rangeQuery("dt").from(since).to(now))
      .execute()
      .actionGet()

    results.hits.totalHits() * 1000 / Config.eventHorizon
  }

  lazy val hitsPerSecondOption = if (clickStreamSecs <= 0) None else Some(hitsScaledToAllServers / clickStreamSecs)

  lazy val minutesOfData = Config.eventHorizon / 1000 / 60

  lazy val debugInfo =
    "hits = %d, timePeriodSecs = %d, hps = %s" format (totalHits, clickStreamSecs, hitsPerSecond)

  def diff(newAll: List[HitReport], newContent: List[HitReport], newOther: List[HitReport], from: DateTime, to: DateTime, totalHits: Long) = {

    copy(
      all = all.diff(newAll take 10),
      everything = everything.diff(newAll),
      content = content.diff(newContent take 20),
      other = other.diff(newOther take 20),
      lastUpdated = to,
      firstUpdated = from,
      totalHits = totalHits,
      clickStreamSecs = (to.getMillis - from.getMillis) / 1000
    )
  }

  def isContent(h: HitReport) = contentMatch.findFirstIn(h.url).isDefined
}

object ListsOfStuff {
  val contentMatch = """/\d{4}/\w{3}/\d{2}""".r
}

case class TopHits(hits: List[HitReport] = Nil) {

  def diff(newList: List[HitReport]): TopHits = {
    TopHits(newList.zipWithIndex map { case (hit, idx) => diffedHit(hit, idx) })
  }

  def diffedHit(newHitReport: HitReport, newIdx: Int) = {
    val currentHitReportIdx = hits.indexWhere(_.url == newHitReport.url)

    val movement =
      if (currentHitReportIdx == -1) NewEntry()
      else if (newIdx < currentHitReportIdx) Up()
      else if (newIdx > currentHitReportIdx) Down()
      else Unchanged()

    newHitReport.copy(movement = movement)
  }
}