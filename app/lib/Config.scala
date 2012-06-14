package lib

import play.api.Play
import java.io.File
import sbt.IO

object Config {
  import play.api.Play.current

  lazy val eventHorizon = envConfig.getMilliseconds("clickStream.eventRetentionPeriod").get
  lazy val scalingFactor = 1
  lazy val host = envConfig.getString("host").getOrElse("localhost:9000")

  lazy val envConfig = Play.configuration.getConfig(stage).getOrElse(
    throw new Error("No configuration found for stage: %s" format (stage)))

  lazy val stageFile = new File("/etc/stage")
  lazy val stage = if (stageFile.exists) IO.read(stageFile).stripSuffix("\n") else "DEV"
}
