package controllers

import _root_.play.api.mvc.Action
import com.gu.management._
import lib.Backend
import com.gu.management.play.{PlayHttpResponse, PlayHttpRequest, ManagementController}

object Switches {
  lazy val all = Healthcheck.switch :: Nil
}

object Metrics {
  lazy val all = pps :: Nil

  val pps: Metric = GaugeMetric("hits", "hps", "Hits per second", "Page views per second as recorded by Ophan",
    () => Backend.viewsPerSecond)
}

object Management extends ManagementController {
  val alternativeHealthCheckPage = new HealthcheckManagementPage() {
    override val path = "/health-check"
  }

  val applicationName = "Live Dashboard"
  lazy val pages =
    new HealthcheckManagementPage() ::
    alternativeHealthCheckPage ::
    new ManifestPage() ::
    new Switchboard(Switches.all, applicationName) ::
    new StatusPage(applicationName, () => Metrics.all) ::
    Nil
}