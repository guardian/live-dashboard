package controllers

import play.api._
import cache.Cached
import play.api.mvc._
import play.api.libs.concurrent._
import net.liftweb.json._
import lib.{ ElasticSearch, Backend }
import org.joda.time.DateTime
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.search.facet.datehistogram.{ DateHistogramFacet, DateHistogramFacetBuilder }
import scala.collection.JavaConverters._

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

    log.info("query took " + results.took())

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

}
