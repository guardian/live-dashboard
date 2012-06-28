package lib

import play.api.Logger
import java.net.InetAddress
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.NodeBuilder
import collection.JavaConverters._
import org.joda.time.{ LocalDate, DateTime }
import org.joda.time.format.ISODateTimeFormat
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.index.query.QueryBuilders._
import org.elasticsearch.index.query.TermFilterBuilder
import org.elasticsearch.search.facet.terms.{ TermsFacet, TermsFacetBuilder }

object ElasticSearch {
  private lazy val log = Logger(getClass)

  private lazy val hostname = InetAddress.getLocalHost.getHostName
  private lazy val settings = ImmutableSettings.settingsBuilder()
    .put("node.name", "live-dashboard-" + hostname)
    .put(Config.elasticsearchOptions.asJava)
    .build()

  private lazy val node = {
    log.info("Creating elastic search node with settings:\n" + settings.getAsMap.asScala.mkString("\n"))

    NodeBuilder.nodeBuilder()
      .settings(settings)
      .node()
  }

  lazy val client = if (Config.stage != "DEV") node.client() else
    new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress("ec2-54-247-37-238.eu-west-1.compute.amazonaws.com", 9300))

  def indexNameForDate(dt: DateTime) = indexNameForDay(dt.toLocalDate)
  def indexNameForDay(dt: LocalDate) = "ophan-" + ISODateTimeFormat.date.print(dt)

  def hitReportFor(url: String) = {
    import scala.collection.JavaConversions._
    val to = Backend.lastUpdated
    val from = Backend.updateWindowStart

    val pageResponse = ElasticSearch.client.prepareSearch(ElasticSearch.indexNameForDate(to))
      .setQuery(
        filteredQuery(
          rangeQuery("dt").from(from).to(to),
          new TermFilterBuilder("url", url))
      )
      .addFields("dt", "url", "documentReferrer", "previousPageSelector", "previousPageElemHash")
      .addFacet(new TermsFacetBuilder("referrrers").field("referringHost").size(10))
      .setSize(0)
      .execute()
      .actionGet()

    val totalPageHits = pageResponse.hits.totalHits

    val secondsOfData = Config.eventHorizon / 1000

    HitReport(url = url,
      percent = (totalPageHits.toDouble * 100) / Backend.totalHits.toDouble,
      hits = totalPageHits.toInt,
      hitsPerSec = (totalPageHits.toDouble / secondsOfData) * Config.scalingFactor,
      topReferrers = for (referrer <- pageResponse.facets().facet[TermsFacet]("referrrers"))
        yield Referrer(referrer.term, referrer.count)
    )
  }
}
