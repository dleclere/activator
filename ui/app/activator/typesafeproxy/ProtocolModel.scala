package activator.typesafeproxy

import akka.actor._

trait Request[+Resp] {
  def sendTo: ActorRef
  final def response[T >: Resp](in: T)(implicit sender: ActorRef): Unit = sendTo.tell(in, sender)
}
