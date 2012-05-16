package lib

import play.api.Play
import java.io.File
import sbt.IO

object Config {
  import play.api.Play.current

  lazy val eventHorizon = Play.configuration.getMilliseconds("clickStream.eventRetentionPeriod").get
  lazy val scalingFactor = Play.configuration.getInt("scalingFactor").get

  lazy val stageFile = new File("/etc/stage")
  lazy val stage = if (stageFile.exists) IO.read(stageFile).stripSuffix("\n") else "DEV"

  lazy val hostForEnv = Map(
    "PROD" -> "live-dashboard.ophan.co.uk",
    "QA" -> "qa-live-dashboard.ophan.co.uk"
  )

  lazy val host = hostForEnv.getOrElse(stage, "localhost:9000")
}
