package lib

import play.api.Play
import java.io.File
import sbt.IO

object Config {
  import play.api.Play.current

  lazy val eventHorizon = envConfig.getMilliseconds("clickStream.eventRetentionPeriod").get
  lazy val listenToMessageQueue = envConfig.getBoolean("listenToZeroMQ").getOrElse(false)
  // 10 is a magic number here : I know we're sampling 2 servers, and we have 20 in total
  lazy val scalingFactor = if (listenToMessageQueue) 10 else 1
  lazy val host = envConfig.getString("host").getOrElse("localhost:9000")

  lazy val envConfig = Play.configuration.getConfig(stage).getOrElse(
    throw new Error("No configuration found for stage: %s" format (stage)))

  lazy val stageFile = new File("/etc/stage")
  lazy val stage = if (stageFile.exists) IO.read(stageFile).stripSuffix("\n") else "DEV"
}
