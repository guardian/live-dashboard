package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent._
import net.liftweb.json.Serialization
import lib.Backend


object PerPageStats extends Controller {

  case class LinkCount(sel: String, hash: String, count: Int)

  def apply(page: String, callback: Option[String] = None) = Action {
    Async {
      Backend.eventsFrom(page).asPromise map { events =>
        withCallback(callback) {
          val eventMap = events groupBy { e => (e.sel.getOrElse(""), e.hash.getOrElse("")) }

          val linkCounts = for {
            ((selector, hash), clicks) <- eventMap
          } yield LinkCount(selector, hash, clicks.size)

          Serialization.write(linkCounts.toList)
        }
      }
    }
  }

  def withCallback(callback: Option[String])(block: => String) = {
    Ok(callback map { x => "%s(%s)" format (x, block)} getOrElse block) as ("application/javascript")
  }
}
