package controllers

import play.api._
import play.api.mvc._
import lib.{HitReport, Backend}
import com.gu.openplatform.contentapi.model.{MediaAsset, Tag, Content => ApiContent}
import org.joda.time.{DateTimeZone, DateTime}

object Application extends Controller {
  
  def index = AuthAction { Ok(views.html.index(Backend.currentLists)) }

  def about = Action { Ok(views.html.about()) }

  def top10 = AuthAction { Ok(views.html.top10(Backend.currentLists.all)) }
  def top10chart = AuthAction { Ok(views.html.snippets.top10chart(Backend.currentLists.all)) }

  def top20 = AuthAction { Ok(views.html.top20(Backend.currentLists))}
  def top20chart = AuthAction { Ok(views.html.snippets.top20chart(Backend.currentLists)) }

  def details = AuthAction { Ok(views.html.details(Backend.currentLists.everything)) }

  def detail(id: String) = AuthAction { Ok(views.html.detail(
    Backend.currentLists.everything.hits.find(_.id == id).get)
  )}

  def search = AuthAction { Ok(views.html.search()) }

  def userAgents = AuthAction { Ok(views.html.userAgents() ) }

  def userAgentsChart = AuthAction { Ok(views.html.snippets.userAgentsChart()) }

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
        c.webPublicationDate.withZone(london), c.webUrl, c.webTitle,
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
  lazy val london = DateTimeZone.forID("Europe/London")

  def content = Action {
    Ok(views.html.content(publishedContent))
  }
  def contentChart = Action {
    Ok(views.html.snippets.contentChart(publishedContent))
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