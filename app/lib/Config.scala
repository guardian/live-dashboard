package lib

import play.api.Play

object Config {
  import play.api.Play.current

  lazy val eventHorizon = Play.configuration.getMilliseconds("clickStream.eventRetentionPeriod").get
  lazy val scalingFactor = Play.configuration.getInt("scalingFactor").get
}
