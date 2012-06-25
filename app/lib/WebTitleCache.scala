package lib

import play.api.cache.Cache
import java.net.{ URI, URL }
import play.api.Logger
import akka.dispatch.Future
import com.gu.openplatform.contentapi.Api

object WebTitleCache {

  import play.api.Play.current

  lazy val log = Logger(getClass)

  def webTitleFor(url: String): String = {
    val uri = new URI(url)
    val path = uri.getPath

    Cache.getAs[String](url) orElse {
      val result = lookupInRecentContent(path)
      result.foreach { title =>
        log.info("Found in recent content %s -> %s" format (url, title))
        Cache.set(url, title)
      }
      result
    } orElse {
      if (uri.getHost != "www.guardian.co.uk") {
        log.info("cannot look up: " + uri)
      } else {
        log.info("Scheduling api lookup for %s..." format uri.getPath)
        scheduleApiLookup(uri.getPath) onSuccess {
          case Some(webTitle) =>
            log.info("Api lookup %s -> %s" format (url, webTitle))
            Cache.set(url, webTitle)
        }
      }
      None
    } getOrElse {
      path
    }
  }

  private def lookupInRecentContent(path: String): Option[String] =
    Backend.publishedContent.find(_.url.endsWith(path)).map(_.title)

  private def scheduleApiLookup(path: String) = {
    import Backend.system

    Future {
      for (c <- Api.item.itemId(path).response.content) yield {
        log.info("Found " + c.webTitle)
        c.webTitle
      }
    }

  }
}
