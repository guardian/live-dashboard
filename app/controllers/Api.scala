package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent._
import net.liftweb.json._
import lib.Backend
import org.joda.time.DateTime

object Api extends Controller {
  implicit val formats = Serialization.formats(NoTypeHints) ++ ext.JodaTimeSerializers.all


  def withCallback(callback: Option[String])(block: => String) = {
    Ok(callback map { _ + "(" + block + ")" } getOrElse block).as("application/javascript")
  }

  def fullData = Backend.currentHits.map { hit => hit.url -> hit }.toMap

  def countsData = fullData.mapValues(_.tidyHitsPerSec)


  def counts(callback: Option[String]) = Action {
    withCallback(callback) {
      Serialization.write(countsData)
    }
  }

  def search(callback: Option[String], since: Long) = Action {
    withCallback(callback) {
      val response = Backend.liveSearchTerms.filter(_.dt > since).sortBy(_.dt)
      Serialization.write(response)
    }
  }


  case class ApiContent(path: String, title: String, publishDate: Long, publishDateString: String)

  def content(callback: Option[String], since: Long) = Action {
    withCallback(callback) {
      val content = Backend.publishedContent.map { c =>
        ApiContent(
          path = "/" + c.id,
          title = c.webTitle,
          publishDate = c.webPublicationDate.getMillis,
          publishDateString = c.webPublicationDate.toString
        )
      }.filter(_.publishDate > since)

      Serialization.write(content)
    }
  }
}
