package lib

import play.api.Logger
import akka.agent.Agent
import akka.actor.{ Actor, ActorSystem }
import org.scala_tools.time.Imports._
import org.elasticsearch.index.query.{ FilteredQueryBuilder, TermFilterBuilder, QueryBuilders }
import org.elasticsearch.search.SearchHit
import org.elasticsearch.common.unit.TimeValue
import akka.pattern.{ ask }
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import org.elasticsearch.search.facet.filter.{ FilterFacet, FilterFacetBuilder }
import org.elasticsearch.search.facet.terms.{ TermsFacet, TermsFacetBuilder }

class Calculator(implicit sys: ActorSystem) {
  implicit val timeout = Timeout(20, TimeUnit.SECONDS)

  val hitReports = Agent(List[HitReport]())
  val listsOfStuff = Agent(ListsOfStuff())

  def calculate(cs: ClickStream) {
    Logger.info("Recalculating...")
    hitReports sendOff (_ => ESClickStreamFetcher.topPaths.toList)
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

case class ESPageView(dt: DateTime, url: String, queryString: String, documentReferrer: Option[String],
  browserId: String,
  previousPage: Option[String],
  previousPageSelector: Option[String],
  previousPageElemHash: Option[String])

object ESClickStreamFetcher {
  import scala.collection.JavaConversions._
  import QueryBuilders._

  def topPaths = {
    val now = DateTime.now
    val since = now.minusMillis(Config.eventHorizon.toInt)
    val response = ElasticSearch.client.prepareSearch(ElasticSearch.indexNameForDate(now))
      .setQuery(rangeQuery("dt").from(since).to(now))
      .addFacet(new TermsFacetBuilder("urls").field("url").size(100))
      .setSize(0)
      .execute()
      .actionGet()

    val totalHits = response.hits.totalHits
    val secondsOfData = Config.eventHorizon / 1000

    for (page <- response.facets().facet[TermsFacet]("urls")) yield {
      val url = page.term

      val pageResponse = ElasticSearch.client.prepareSearch(ElasticSearch.indexNameForDate(now))
        .setQuery(filteredQuery(rangeQuery("dt").from(since).to(now),
          new TermFilterBuilder("url", url)))
        .addFields("dt", "url", "previousPage", "previousPageSelector", "previousPageElemHash")
        .setSize(100)
        .execute()
        .actionGet()

      val totalPageHits = pageResponse.hits.totalHits

      val events = for {
        x <- pageResponse.hits.hits()
      } yield Event(ip = "", dt = new DateTime(x[Long]("dt").get), url = x[String]("url").getOrElse(""), method = "GET", responseCode = 200,
        referrer = x[String]("previousPage"), userAgent = "", geo = "",
        sel = x[String]("previousPageSelector"),
        hash = x[String]("previousPageElemHash")
      )
      HitReport(url = url,
        percent = totalPageHits.toDouble / totalHits,
        hits = totalPageHits.toInt,
        hitsPerSec = (totalPageHits.toDouble / secondsOfData) * Config.scalingFactor,
        events = events.toList
      )
    }
  }

  implicit def hit2RichHit(hit: SearchHit): RichHit = new RichHit(hit)
  class RichHit(hit: SearchHit) {
    val theHit = Option(hit)
    def apply[T](fieldName: String) = for {
      h <- theHit
      field <- Option(h.field(fieldName))
    } yield field.value[T]()
  }

}