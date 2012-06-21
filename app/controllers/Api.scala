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
}
