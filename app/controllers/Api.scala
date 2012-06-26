package controllers

import play.api._
import cache.Cached
import play.api.mvc._
import play.api.libs.concurrent._
import net.liftweb.json._
import lib.{ ElasticSearch, Backend }
import org.joda.time.DateTime
import org.elasticsearch.index.query.{ FilterBuilders, TermFilterBuilder, QueryBuilders }
import org.elasticsearch.search.facet.datehistogram.{ DateHistogramFacet, DateHistogramFacetBuilder }
import scala.collection.JavaConverters._
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.search.facet.terms.{ TermsFacet, TermsFacetBuilder }

object Api extends Controller {
  lazy val log = Logger(getClass)
  implicit val formats = Serialization.formats(NoTypeHints) ++ ext.JodaTimeSerializers.all

  def withCallback(callback: Option[String])(block: => String) = {
    Ok(callback map { _ + "(" + block + ")" } getOrElse block).as("application/javascript")
  }

  def fullData = Backend.currentHits.map { hit => hit.url -> hit }.toMap

  def countsData = fullData.mapValues(_.tidyHitsPerSec)

  def counts(callback: Option[String]) = Action {
    withCallback(callback) {
      Serialization.write(countsData)
    }
  }

  private def getCounts(fromDt: DateTime, toDt: DateTime, requiredInterval: String = "second") = {
    val results = ElasticSearch.client.prepareSearch(ElasticSearch.indexNameForDate(DateTime.now))
      .setSize(0)
      .setQuery(QueryBuilders.rangeQuery("dt").from(fromDt).to(toDt))
      .addFacet(new DateHistogramFacetBuilder("dts").field("dt").interval(requiredInterval))
      .execute()
      .actionGet()

    log.info("getCounts query took " + results.took())

    val facet = results.facets().facet(classOf[DateHistogramFacet], "dts")

    facet.entries().asScala.map { entry =>
      entry.time() -> entry.count()
    }
  }

  def pageViews(callback: Option[String] = None) = Action {
    val now = DateTime.now()
    val from = now.minusHours(8).withSecondOfMinute(0).withMillisOfSecond(0)

    val counts = getCounts(from, now, "minute").map(_.productIterator.toList)

    withCallback(callback) {
      Serialization.write(counts)
    }
  }

  case class UrlCounts(url: String, count: Int)

  import FilterBuilders._

  private def mostReadForUrlsStartingWith(url: String): Seq[UrlCounts] = {
    val toDate = DateTime.now
    val fromDate = toDate.minusHours(24)

    val results = ElasticSearch.client
      .prepareSearch(ElasticSearch.indexNameForDate(fromDate), ElasticSearch.indexNameForDate(toDate))
      .setSize(0)
      .setQuery(rangeQuery("dt").from(fromDate).to(toDate))
      .addFacet(
        new TermsFacetBuilder("urls").field("url").size(20).facetFilter(
          andFilter(
            termFilter("isContent", true),
            prefixFilter("url", url)
          )
        )
      )
      .execute()
      .actionGet()

    log.info("mostRead query took " + results.took())

    results.facets.facet(classOf[TermsFacet], "urls").entries.asScala.map { entry =>
      UrlCounts(entry.term, entry.count)
    }
  }

  def mostReadForSection(section: String) = Action {
    Ok(Serialization.write(mostReadForUrlsStartingWith("http://www.guardian.co.uk/" + section)))
      .as("application/javascript")
  }

  def mostRead() = Action {
    Ok(Serialization.write(mostReadForUrlsStartingWith("http://www.guardian.co.uk/")))
      .as("application/javascript")
  }

}
