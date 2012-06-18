package lib

import java.io.File
import sbt.IO
import play.api.{ Configuration, Play }
import com.typesafe.config.ConfigFactory

object Config {
  import play.api.Play.current

  lazy val eventHorizon = envConfig.getMilliseconds("clickStream.eventRetentionPeriod").get
  lazy val scalingFactor = 1
  lazy val host = envConfig.getString("host").getOrElse("localhost:9000")

  lazy val envConfig = Play.configuration.getConfig(stage).getOrElse(
    throw new Error("No configuration found for stage: %s" format (stage)))

  lazy val stageFile = new File("/etc/stage")
  lazy val stage = if (stageFile.exists) IO.read(stageFile).stripSuffix("\n") else "DEV"

  lazy val elasticsearchOptionsFromConfig = envConfig.getConfig("elasticsearch")

  private lazy val localPropsFile = System.getProperty("user.home") + "/.ophan-ec2"
  private lazy val machineSpecificAwsConfig = fileConfig(
    if (stage == "DEV") localPropsFile else "/etc/aws.conf"
  )
  lazy val accessKey = machineSpecificAwsConfig.getString("accessKey").get
  lazy val secretKey = machineSpecificAwsConfig.getString("secretKey").get

  lazy val elasticsearchKeys = Map(
    "cloud.aws.access_key" -> accessKey,
    "cloud.aws.secret_key" -> secretKey
  )

  lazy val elasticsearchOptions = elasticsearchOptionsFromConfig.toMap ++ elasticsearchKeys

  def fileConfig(filePath: String) = {
    val file = new File(filePath)
    if (!file.exists) throw new Error("Could not find %s" format (filePath))
    Configuration(ConfigFactory.parseFile(file))
  }

  implicit def config2ToMappable(config: Option[Configuration]): ToMappable = new ToMappable(config)
  class ToMappable(config: Option[Configuration]) {
    def toMap: Map[String, String] = config
      .map(c => c.keys.map(k => k -> c.getString(k).get).toMap)
      .getOrElse(Map.empty[String, String])
  }
}

