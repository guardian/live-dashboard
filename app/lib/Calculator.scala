package lib

import play.api.Logger
import akka.agent.Agent
import akka.actor.ActorSystem
import org.scala_tools.time.Imports._
import org.elasticsearch.index.query.{ TermFilterBuilder, QueryBuilders }
import akka.util.Timeout
import java.util.concurrent.TimeUnit
import org.elasticsearch.search.facet.terms.{ TermsFacet, TermsFacetBuilder }
import org.elasticsearch.action.search.SearchResponse

class Calculator(implicit sys: ActorSystem) {
  implicit val timeout = Timeout(20, TimeUnit.SECONDS)

  val hitReports = Agent(List[HitReport]())
  val listsOfStuff = Agent(ListsOfStuff())

  def calculate() {
    Logger.info("Recalculating...")
    val now = DateTime.now
    val since = now.minusMillis(Config.eventHorizon.toInt)
    Logger.info("X")
    val (totalHits, reports) = ESClickStreamFetcher.topPaths(since, now)
    Logger.info("Y")
    Logger.info(totalHits.toString)
    hitReports send (_ => reports.toList)
    listsOfStuff sendOff (_.diff(hitReports.get(), since, now, totalHits))
    Logger.info("%s hits found" format (totalHits))
  }
}

object ESClickStreamFetcher {
  import scala.collection.JavaConversions._
  import QueryBuilders._

  def topPaths(from: DateTime, to: DateTime) = {
    val response = ElasticSearch.client.prepareSearch(ElasticSearch.indexNameForDate(to))
      .setQuery(rangeQuery("dt").from(from).to(to))
      .addFacet(new TermsFacetBuilder("urls").field("url").size(100))
      .setSize(0)
      .execute()
      .actionGet()

    val totalHits = response.hits.totalHits
    Logger.info("Z")

    val secondsOfData = Config.eventHorizon / 1000

    val hitReports = hitReportsFrom(response, from, to, totalHits, secondsOfData)
    (totalHits, hitReports)
  }

  def hitReportsFrom(response: SearchResponse, from: DateTime, to: DateTime, totalHits: Long, secondsOfData: Long) =
    for (page <- response.facets().facet[TermsFacet]("urls")) yield {
      val url = page.term
      Logger.info(url)

      val pageResponse = ElasticSearch.client.prepareSearch(ElasticSearch.indexNameForDate(to))
        .setQuery(filteredQuery(rangeQuery("dt").from(from).to(to),
          new TermFilterBuilder("url", url)))
        .addFields("dt", "url", "documentReferrer", "previousPageSelector", "previousPageElemHash")
        .addFacet(new TermsFacetBuilder("referrrers").field("referringHost").size(10))
        .setSize(100)
        .execute()
        .actionGet()

      val totalPageHits = pageResponse.hits.totalHits

      HitReport(url = url,
        percent = (totalPageHits.toDouble * 100) / totalHits.toDouble,
        hits = totalPageHits.toInt,
        hitsPerSec = (totalPageHits.toDouble / secondsOfData) * Config.scalingFactor,
        topReferrers = for (referrer <- pageResponse.facets().facet[TermsFacet]("referrrers"))
          yield Referrer(referrer.term, referrer.count)
      )
    }
}