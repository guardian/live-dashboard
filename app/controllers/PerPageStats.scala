package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent._
import net.liftweb.json.Serialization
import lib.Backend


object PerPageStats extends Controller {

  case class LinkDetail(sel: String, hash: String, count: Int)

  def apply(page: String, callback: Option[String] = None) = Action {
    Async {
      Backend.eventsFrom(page).asPromise map { events =>
        withCallback(callback) {
          // need group by and to actually get selection/hash from original source
          Serialization.write(events map (event => LinkDetail(event.)))
        }
      }
    }
  }

  def withCallback(callback: Option[String])(block: => String) = {
    Ok(callback map { x => "%s(%s)" format (x, block)} getOrElse block) as ("application/javascript")
  }
}
