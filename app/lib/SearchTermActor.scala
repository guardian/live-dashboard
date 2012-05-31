package lib

import akka.actor.Actor
import org.jboss.netty.handler.codec.http.QueryStringDecoder
import scala.collection.JavaConversions._
import org.joda.time.DateTime


case class GuSearchTerm(
  dt: Long,
  q: String,
  otherParams: Map[String, String]
)

class SearchTermActor extends Actor {
  import SearchTermActor._
  private var terms: List[GuSearchTerm] = Nil

  protected def receive = {
    case e: Event =>
      if (e.path.endsWith("/search")) {
        val params = new QueryStringDecoder(e.url).getParameters.map{ case (k, v) => k -> v.head }.toMap
          .filter { case (k, v) => v.length > 0}

        for (q <- params.get("q")) {
          terms = (GuSearchTerm(System.currentTimeMillis(), q, params - "q") :: terms).take(20)
        }
      }

    case GetSearchTerms => sender ! terms
  }
}

object SearchTermActor {
  sealed trait Messages
  case object GetSearchTerms
}

