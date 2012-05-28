package controllers

import _root_.play.api.mvc.{Controller, Action}
import com.gu.management.play.ManagementController
import com.gu.management._
import lib.Backend

object Switches {
  lazy val all = Healthcheck.switch :: Nil
}

object Metrics {
  lazy val all = pps :: RequestMetrics.all

  val pps: Metric = GaugeMetric("hits", "hps", "Hits per second", "Page views per second as recorded by Ophan",
    () => Backend.viewsPerSecond)
}

object Management extends ManagementController {
  val applicationName = "Live Dashboard"
  lazy val pages =
    new HealthcheckManagementPage() ::
      new ManifestPage() ::
      new Switchboard(Switches.all, applicationName) ::
      new StatusPage(applicationName, () => Metrics.all) ::
      Nil

  def healthCheck() = Action {
    Redirect("/management/healthcheck")
  }
}

object RequestTimingMetric extends TimingMetric("performance", "request", "Request", "time taken to serve requests")

object Request200s extends CountMetric("request-status", "200_ok", "200 Ok", "number of pages that responded 200")
object Request50xs extends CountMetric("request-status", "50x_error", "50x Error", "number of pages that responded 50x")
object Request404s extends CountMetric("request-status", "404_not_found", "404 Not found", "number of pages that responded 404")
object Request30xs extends CountMetric("request-status", "30x_redirect", "30x Redirect", "number of pages that responded with a redirect")
object RequestOther extends CountMetric("request-status", "other", "Other", "number of pages that responded with an unexpected status code")

object RequestMetrics {
  lazy val all = List(
    RequestTimingMetric,
    Request200s,
    Request50xs,
    Request404s,
    RequestOther,
    Request30xs)
}