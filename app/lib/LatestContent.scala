package lib

import akka.agent._
import com.gu.openplatform.contentapi.model._
import org.joda.time.DateTime
import com.gu.openplatform.contentapi.Api
import akka.actor.ActorSystem
import akka.event.Logging
import play.api.libs.ws.WS
import play.api.libs.concurrent.Promise
import scala.Some
import com.gu.openplatform.contentapi.model.Content
import com.gu.openplatform.contentapi.model.Tag

case class LiveDashboardContent(
    content: Content,
    isLead: Option[Boolean] = None) {
  lazy val isCommentable = content.safeFields.get("commentable").exists(_ == "true")
}

object LiveDashboardContent {
  // this is a temporary hack :)
  implicit def ourContentToApiContent(c: LiveDashboardContent): Content = c.content
}

object LatestContent {
  val editorialSections = "artanddesign | books | business | childrens-books-site | commentisfree | " +
    "crosswords | culture | education | environment | fashion | film | football | theguardian | " +
    "theobserver | global | global-development | law | lifeandstyle | media | money | music | news | " +
    "politics | science | society | sport | stage | technology | tv-and-radio | travel | uk | world"
}

class LatestContent(implicit sys: ActorSystem) {
  val apiKey = "d7bd4fkrbgkmaehrfjsbcetu"
  Api.apiKey = Some(apiKey)

  private val log = Logging(sys, this.getClass)
  val latest = Agent[List[PublishedContent]](Nil)

  def refresh() {

    log.info("Getting latest content published since " + new DateTime().minusHours(4) + "...")

    val apiNewContent: List[Content] =
      Api.search.fromDate(new DateTime().minusHours(4)).showTags("all")
        .orderBy("newest").showFields("trailText,commentable")
        .showMedia("picture")
        .section(LatestContent.editorialSections)
        .pageSize(50).results

    latest.sendOff(_ => asPublishedContent(apiNewContent))
  }

  private def asPublishedContent(contentList: List[Content]): List[PublishedContent] = {
    def leadContentForTag(tagId: String): Promise[Seq[String]] = {
      WS.url("http://content.guardianapis.com/%s.json" format tagId)
        .withQueryString("api-key" -> apiKey)
        .get()
        .map { r => if (r.body.startsWith("<")) { log.info("*** tagId = " + tagId + " *** " + r.body) }; r }
        .map { r => (r.json \ "response" \ "leadContent" \\ "id").map(_.as[String]) }
    }

    val leadSections = contentList.flatMap(_.sectionId).sorted.distinct

    log.info("Getting lead content status for " + leadSections)

    val leadItemsPromise = for {
      section <- leadSections
    } yield {
      val sectionTag = section + "/" + section
      section -> leadContentForTag(sectionTag)
    }

    log.debug("leadItemsPromise = " + leadItemsPromise)

    // now redeem those promises
    val leadItems = leadItemsPromise.flatMap {
      case (section, promise) =>
        promise.await.fold(
          onError = { t =>
            log.error(t, "error getting info for section %s" format section)
            Nil
          },
          onSuccess = { r => List(section -> r) }
        )
    }.toMap

    log.debug("leadItems = " + leadItems)

    val result = contentList.map { c =>
      val hitReport = ElasticSearch.hitReportFor(c.webUrl)
      val section = c.sectionName.getOrElse("")
      PublishedContent(c.webPublicationDate, c.webUrl, c.webTitle,
        hitReport.tidyHitsPerSec, section, c.safeFields.get("trailText"),
        c.tags, Some(hitReport), altTextOfMainImageFor(c), isLead(c, leadItems),
        Backend.ukFrontLinkTracker.links().contains(c.id),
        Backend.usFrontLinkTracker.links().contains(c.id),
        c.safeFields.get("commentable").exists(_ == "true"))
    }

    log.info("Lead content processing complete")

    result
  }

  def altTextOfMainImageFor(c: Content): Option[String] = {
    val mainPic = c.mediaAssets.find(m => m.rel == "body" && m.index == 1) orElse
      c.mediaAssets.find(m => m.rel == "gallery" && m.index == 1)

    mainPic flatMap { m => m.fields.getOrElse(Map()).get("altText") }
  }

  def isLead(c: Content, leadItems: Map[String, Seq[String]]) = {
    val section = c.sectionId.get
    val leadList = leadItems.get(section).getOrElse(Nil)

    leadList contains c.id
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
    isCommentable: Boolean) {
  lazy val cpsCssClass = hitsPerSec match {
    case "0" => "zero"
    case s if s.startsWith("0") => ""
    case "<0.1" => ""
    case "trace" => ""
    case _ => "high"
  }
}
