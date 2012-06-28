package controllers

import play.api._
import cache.Cached
import play.api.mvc._
import play.api.libs.concurrent._
import net.liftweb.json._
import lib.{ ElasticSearchPromise, ElasticSearch, Backend }
import org.joda.time.{ Duration, DateTime }
import org.elasticsearch.index.query.{ FilterBuilder, FilterBuilders, TermFilterBuilder, QueryBuilders }
import org.elasticsearch.search.facet.datehistogram.{ DateHistogramFacet, DateHistogramFacetBuilder }
import scala.collection.JavaConverters._
import org.elasticsearch.search.facet.terms.{ TermsFacet, TermsFacetBuilder }

import FilterBuilders._
import org.elasticsearch.search.facet.FacetBuilders._
import QueryBuilders._

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

  import ElasticSearchPromise._

  private def getCounts(fromDt: DateTime, toDt: DateTime,
    requiredInterval: String = "minute",
    addToTime: Long = 0,
    filter: FilterBuilder = FilterBuilders.matchAllFilter()) =

    ElasticSearch.client.prepareSearch(ElasticSearch.indexNameForDate(fromDt), ElasticSearch.indexNameForDate(toDt))
      .setSize(0)
      .setQuery(rangeQuery("dt").from(fromDt).to(toDt))
      .addFacet(
        dateHistogramFacet("dts").field("dt").interval(requiredInterval).facetFilter(filter)
      )
      .execute()
      .asPromise
      .map { results =>
        log.info("getCounts from " + fromDt + " query took " + results.took())

        val facet = results.facets().facet(classOf[DateHistogramFacet], "dts")

        val list = facet.entries().asScala.toList.map { entry =>
          List(entry.time() + addToTime, entry.count())
        }

        if (list.size > 2)
          // drop off the first and last values, they probably represent incomplete buckets
          list.tail.init
        else
          list
      }

  case class CountsResponse(today: List[List[Long]], lastWeek: List[List[Long]])

  private def pageViewsWithFilter(callback: Option[String], filter: FilterBuilder): AsyncResult = {
    // rouding "now" off to the resolution we're interested in, in the
    // (unproved) hope of enabling elasticsearch to cache the query a bit more
    val now = DateTime.now().withMillisOfSecond(0).withSecondOfMinute(0)
    val from = now.minusHours(8)

    // add 7 days onto last weeks dateTimes so we can compare against today
    val oneWeek = Duration.standardDays(7).getMillis

    Async {
      for {
        today <- getCounts(from, now, "minute", filter = filter)
        lastWeek <- getCounts(from.minusWeeks(1), now.minusWeeks(1), "minute", addToTime = oneWeek, filter = filter)
      } yield {
        withCallback(callback) {
          Serialization.write(CountsResponse(today, lastWeek))
        }
      }
    }
  }

  def pageViews(callback: Option[String] = None, url: Option[String] = None, section: Option[String]) = Action {
    val filter =
      url.map(termFilter("url", _))
        .orElse(section.map(s => prefixFilter("url", "http://www.guardian.co.uk/" + s)))
        .getOrElse(
          orFilter(
            prefixFilter("url", "http://www.guardian.co.uk"),
            prefixFilter("url", "http://www.guardiannews.com")
          )
        )

    pageViewsWithFilter(callback, filter)
  }

  def pageViewsForSection(section: String, callback: Option[String] = None) = Action {
    pageViewsWithFilter(callback, prefixFilter("url", "http://www.guardian.co.uk/" + section))
  }

  case class UrlCounts(url: String, count: Int)

  private def mostReadForUrlsStartingWith(url: String): Promise[Seq[UrlCounts]] = {
    val toDate = DateTime.now
    val fromDate = toDate.minusHours(24)

    ElasticSearch.client
      .prepareSearch(ElasticSearch.indexNameForDate(fromDate), ElasticSearch.indexNameForDate(toDate))
      .setSize(0)
      .setQuery(rangeQuery("dt").from(fromDate).to(toDate))
      .addFacet(
        termsFacet("urls").field("url").size(20).facetFilter(
          andFilter(
            termFilter("isContent", true),
            prefixFilter("url", url)
          )
        )
      )
      .execute
      .asPromise
      .map { results =>
        log.info("mostRead query took " + results.took())

        results.facets.facet(classOf[TermsFacet], "urls").entries.asScala.map { entry =>
          UrlCounts(entry.term, entry.count)
        }
      }

  }

  def mostReadForSection(section: String) = Action {
    Async {
      mostReadForUrlsStartingWith("http://www.guardian.co.uk/" + section).map { result =>
        Ok(Serialization.write(result)).as("application/javascript")
      }
    }
  }

  def mostRead() = Action {
    Async {
      mostReadForUrlsStartingWith("http://www.guardian.co.uk/").map { result =>
        Ok(Serialization.write(result)).as("application/javascript")
      }
    }
  }

}
