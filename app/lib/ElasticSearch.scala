package lib

import play.api.Logger
import java.net.InetAddress
import org.elasticsearch.common.settings.ImmutableSettings
import org.elasticsearch.node.NodeBuilder
import collection.JavaConverters._
import org.joda.time.{ LocalDate, DateTime }
import org.joda.time.format.ISODateTimeFormat
import org.elasticsearch.client.transport.TransportClient
import org.elasticsearch.common.transport.{ InetSocketTransportAddress, TransportAddress }
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.ec2.{ AmazonEC2Client, AmazonEC2 }
import com.amazonaws.services.ec2.model.{ InstanceStateName, Instance }

object ElasticSearch {
  private lazy val log = Logger(getClass)

  private lazy val hostname = InetAddress.getLocalHost.getHostName
  private lazy val settings = ImmutableSettings.settingsBuilder()
    .put("node.name", "serf-" + hostname)
    .put(Config.elasticsearchOptions.asJava)
    .build()

  private lazy val node = {
    log.info("Creating elastic search node with settings:\n" + settings.getAsMap.asScala.mkString("\n"))

    NodeBuilder.nodeBuilder()
      .settings(settings)
      .node()
  }

  lazy val client = if (Config.stage != "DEV") node.client() else
    new TransportClient(settings).addTransportAddress(new InetSocketTransportAddress("localhost", 9300))

  def indexNameForDate(dt: DateTime) = indexNameForDay(dt.toLocalDate)
  def indexNameForDay(dt: LocalDate) = "ophan-" + ISODateTimeFormat.date.print(dt)
}
