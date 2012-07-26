package controllers

import play.api._
import play.api.mvc._
import org.joda.time.DateTime
import lib.{HitReport, Backend}
import com.gu.openplatform.contentapi.model.{MediaAsset, Tag, Content => ApiContent}

object Application extends Controller {
  
  def index(anything: String) = Action { Ok(views.html.index()) }

  private def publishedContent = {

    def altTextOfMainImageFor(c: ApiContent): Option[String] = {
      val mainPic = c.mediaAssets.find(m => m.rel == "body" && m.index == 1) orElse
        c.mediaAssets.find(m => m.rel == "gallery" && m.index == 1)

      mainPic flatMap { m => m.fields.getOrElse(Map()).get("altText") }
    }

    val currentHits = Api.fullData
    val ukFrontLinks = Backend.ukFrontLinkTracker.links()
    val usFrontLinks = Backend.usFrontLinkTracker.links()

    Backend.publishedContent.map { c =>
      PublishedContent(
        c.webPublicationDate, c.webUrl, c.webTitle,
        currentHits.get(c.webUrl).map(_.tidyHitsPerSec).getOrElse("0"),
        c.sectionName.getOrElse(""),
        c.safeFields.get("trailText"),
        c.tags,
        currentHits.get(c.webUrl),
        altTextOfMainImageFor(c),
        c.isLead.getOrElse(false),
        ukFrontLinks.contains(c.id),
        usFrontLinks.contains(c.id),
        c.isCommentable
      )
    }
  }


}


case class PublishedContent(
  publicationDate: DateTime,
  url: String,
  title: String,
  hitsPerSec: String,
  section: String,
  trailText: Option[String], 
  tags: List[Tag],
  hitReport: Option[HitReport],
  altText: Option[String],
  isLead: Boolean,
  onUkFront: Boolean,
  onUsFront: Boolean,
  isCommentable: Boolean
) {
  lazy val cpsCssClass = hitsPerSec match {
    case "0" => "zero"
    case s if s.startsWith("0") => ""
    case "<0.1" => ""
    case "trace" => ""
    case _ => "high"
  }
}