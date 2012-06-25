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
  val lastUpdated = Agent(DateTime.now)
  def updateWindowStart = lastUpdated().minusMillis(Config.eventHorizon.toInt)
  val totalHits = Agent(0L)

  def calculate() {
    Logger.info("Recalculating...")
    lastUpdated send (DateTime.now)
    val (allReports, contentReports, nonContentReports) = topPaths
    hitReports send (allReports.toList)
    listsOfStuff sendOff { current =>
      val newValue = current.diff(
        hitReports.get(),
        contentReports.toList,
        nonContentReports.toList,
        updateWindowStart,
        lastUpdated(),
        totalHits())

      Logger.info(newValue.debugInfo)
      newValue
    }
  }

  import scala.collection.JavaConversions._
  import QueryBuilders._

  def topPaths = {
    val to = lastUpdated()
    val from = updateWindowStart

    val contentResponse = topPagesQuery.setQuery(
      filteredQuery(rangeQuery("dt").from(from).to(to), new TermFilterBuilder("isContent", true)))
      .execute().actionGet()

    val nonContentResponse = topPagesQuery.setQuery(
      filteredQuery(rangeQuery("dt").from(from).to(to), new TermFilterBuilder("isContent", false)))
      .execute().actionGet()

    val allResponse = topPagesQuery.setQuery(rangeQuery("dt").from(from).to(to)).execute().actionGet()

    totalHits send (allResponse.hits.totalHits)

    val contentHitReports = hitReportsFrom(contentResponse)
    val nonContentHitReports = hitReportsFrom(nonContentResponse)
    val allHitReports = hitReportsFrom(allResponse)

    (allHitReports, contentHitReports, nonContentHitReports)
  }

  def topPagesQuery =
    ElasticSearch.client.prepareSearch(ElasticSearch.indexNameForDate(DateTime.now))
      .addFacet(new TermsFacetBuilder("urls").field("url").size(100))
      .setSize(0)

  def hitReportsFrom(response: SearchResponse) = {
    for (page <- response.facets().facet[TermsFacet]("urls").toList.take(10)) yield {
      ElasticSearch.hitReportFor(page.term)
    }
  }
}