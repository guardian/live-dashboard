package lib

import play.api.libs.concurrent._
import org.elasticsearch.action.{ ActionListener, ListenableActionFuture }

object ElasticSearchPromise {
  def apply[A](esResult: ListenableActionFuture[A]): Promise[A] = {
    val promise = new STMPromise[A]
    esResult.addListener(new ActionListener[A] {
      def onFailure(e: Throwable) { promise.throwing(e) }
      def onResponse(response: A) { promise.redeem(response) }
    })
    promise
  }

  implicit def pimpAsPromiseToElasticSearch[A](esFuture: ListenableActionFuture[A]) = new {
    def asPromise: Promise[A] = ElasticSearchPromise(esFuture)
  }
}

