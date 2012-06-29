package controllers

import play.api._
import play.api.mvc._
import lib.{ LatestContent, HitReport, Backend }
import com.gu.openplatform.contentapi.model.{ Content => ApiContent, Section, MediaAsset, Tag }
import org.joda.time.{ DateTimeZone, DateTime }

object Application extends Controller {

  def index = AuthAction { Ok(views.html.index(Backend.currentLists)) }

  def about = Action { Ok(views.html.about()) }
  def changelog = Action { Ok(views.html.changelog()) }

  def top10 = AuthAction { Ok(views.html.top10(Backend.currentLists)) }
  def top10chart = AuthAction { Ok(views.html.snippets.top10chart(Backend.currentLists)) }

  def top20 = AuthAction { Ok(views.html.top20(Backend.currentLists)) }
  def top20chart = AuthAction { Ok(views.html.snippets.top20chart(Backend.currentLists)) }

  def detail(id: String) = AuthAction {
    Ok(views.html.detail(
      Backend.currentLists.everything.hits.find(_.id == id).get)
    )
  }

  lazy val london = DateTimeZone.forID("Europe/London")

  def content = Action {
    Ok(views.html.content(Backend.publishedContent))
  }
  def contentChart = Action {
    Ok(views.html.snippets.contentChart(Backend.publishedContent))
  }

  lazy val sections = com.gu.openplatform.contentapi.Api.sections
    .section(LatestContent.editorialSections)
    .response.results
    .sortBy(_.webTitle)

  //  lazy val sections = List(
  //    Section("sport", "Sport", "a", "b"),
  //    Section("uk", "Uk News", "a", "b"),
  //    Section("world", "World News", "a", "b")
  //  )

  def graph = AuthAction { req =>
    Ok(views.html.graph(sections, req.queryString.get("section").flatMap(_.headOption)))
  }
}
