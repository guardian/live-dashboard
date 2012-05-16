package controllers

import play.api.mvc.{Action, Controller}
import lib.Config
import play.api.http.{Writeable, ContentTypeOf}
import play.api.templates.Html


object HeatMap extends Controller {


  def forEnv() = Action {
    Ok(views.html.heatmapuserjs(Config.host))(
      Writeable[Html](_.toString().stripPrefix("\n").getBytes()), ContentTypeOf(Some("application/json")))
  }
}
