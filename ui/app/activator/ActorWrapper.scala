/**
 * Copyright (C) 2013 Typesafe <http://typesafe.com/>
 */
package activator

import akka.actor.{ PoisonPill, Terminated, Actor, ActorRef }

trait ActorWrapper {
  @volatile var isTerminated = false

  def actorTerminated() {
    isTerminated = true
  }
}

case class ActorWrapperHelper(actor: ActorRef) extends ActorWrapper

class ActorWatcher(watchee: ActorRef, watcher: ActorWrapper) extends Actor {
  context.watch(watchee)
  def receive = {
    case Terminated(_) =>
      watcher.actorTerminated()
      self ! PoisonPill
  }
}
