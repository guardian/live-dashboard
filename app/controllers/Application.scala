package controllers

import play.api._
import play.api.mvc._
import lib.{ HitReport, Backend }
import com.gu.openplatform.contentapi.model.{ MediaAsset, Tag, Content => ApiContent }
import org.joda.time.{ DateTimeZone, DateTime }

object Application extends Controller {

  def index = AuthAction { Ok(views.html.index(Backend.currentLists)) }

  def about = Action { Ok(views.html.about()) }

  def top10 = AuthAction { Ok(views.html.top10(Backend.currentLists.all)) }
  def top10chart = AuthAction { Ok(views.html.snippets.top10chart(Backend.currentLists.all)) }

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

}
