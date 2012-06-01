package lib

import org.jboss.netty.handler.codec.http.QueryStringDecoder
import scala.collection.JavaConversions._
import akka.actor.ActorSystem
import akka.agent.Agent


case class GuSearchTerm(
  dt: Long,
  q: String,
  otherParams: Map[String, String]
)

class SearchTermAgent(implicit sys: ActorSystem) extends EventProcessor {
  val termsAgent = Agent(List[GuSearchTerm]())

  def + (e: Event) {
    if (e.path.endsWith("/search")) {
      termsAgent sendOff { terms =>
        val params = new QueryStringDecoder(e.url).getParameters.map{ case (k, v) => k -> v.head }.toMap
          .filter { case (k, v) => v.length > 0}

        val newTerm = for (q <- params.get("q"))
          yield GuSearchTerm(System.currentTimeMillis(), q, params - "q")

        newTerm map { t => (t :: terms).take(20) } getOrElse terms
      }
    }
  }

  def apply() = termsAgent()
}
