import com.gu.management.play.{StatusCounters, RequestTimer}
import controllers._
import lib.Backend
import play.api._

object Global extends GlobalSettings with RequestTimer with StatusCounters {
  override val requestTimer = RequestTimingMetric
  override val okCounter = Request200s
  override val redirectCounter = Request30xs
  override val notFoundCounter = Request404s
  override val errorCounter = Request50xs

  override def onStart(app: Application) {
    Logger.info("Starting...")
    Backend.start()
    Logger.info("Started")
  }

  override def onStop(app: Application) {
    Logger.info("Stopping...")
    Backend.stop()
    Logger.info("Stopped")

  }
}