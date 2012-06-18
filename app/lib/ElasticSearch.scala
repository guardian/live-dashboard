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

  lazy val client = if (Config.stage != "DEV") node.client() else {
    val tc = new TransportClient(settings)
    for (host <- PoorMansEC2Discovery.hosts) {
      tc.addTransportAddress(new InetSocketTransportAddress(host, 9300))
    }
    tc
  }

  def indexNameForDate(dt: DateTime) = indexNameForDay(dt.toLocalDate)
  def indexNameForDay(dt: LocalDate) = "ophan-" + ISODateTimeFormat.date.print(dt)
}

object PoorMansEC2Discovery {
  import collection.JavaConversions._
  import Config.config2ToMappable

  lazy val credentials = new BasicAWSCredentials(Config.accessKey, Config.secretKey)
  lazy val ec2: AmazonEC2 = {
    val client = new AmazonEC2Client(credentials)
    client.setEndpoint("ec2.%s.amazonaws.com" format (Config.elasticsearchOptions("cloud.aws.region")))
    client
  }

  def instanceResponse = ec2.describeInstances

  def tagValue(instance: Instance, key: String) = instance.getTags.find(_.getKey == key).map(_.getValue).getOrElse("")

  def hosts: Seq[String] = {
    val hostOptions = for {
      res <- instanceResponse.getReservations
      instance <- res.getInstances if instance.getState.getName == InstanceStateName.Running.toString
    } yield {
      val tags = instance.getTags.map(t => t.getKey -> t.getValue).toMap
      val requiredTags = Config.elasticsearchOptionsFromConfig.flatMap(_.getConfig("discovery.ec2.tags")).toMap
      if (requiredTags.forall(x => tags.exists(_ == x))) Some(instance.getPublicDnsName) else None
    }
    hostOptions.flatten
  }
}
