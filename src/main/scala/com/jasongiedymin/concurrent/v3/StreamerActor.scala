/*******************************************************************************
 * This software is licensed under the Apache 2 license, quoted below.
 *
 * Copyright (C) 2013 Jason Giedymin (jasong@apache.org,
 * http://jasongiedymin.com, https://github.com/JasonGiedymin)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     [http://www.apache.org/licenses/LICENSE-2.0]
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/

package com.jasongiedymin.concurrent.v3

/**
 * Warning: Use Scala 2.10 only, and make sure to do sbt clean very
 * often...
 *
 * Actor based
 */

import akka.actor._
import akka.event.Logging
import akka.routing.SmallestMailboxRouter
import scala.concurrent.duration._

trait Data {
  type DataType = Int
  type DataStream = Stream[DataType]
}


object ReaderMessages extends Data {
  case class Read(data:DataType)
}

object ReadActor {
  def apply(worker:ActorRef) = new ReadActor(worker)
}
class ReadActor(worker:ActorRef) extends Actor {
  import ReaderMessages._
  import WorkerMessages._
  val log = Logging(context.system, this)

  val writer = context.actorOf(Props(WriteActor()), "writer")

  def receive = {
    case Read(data) => {
      log.info("received data: %s" format data)
      worker ! Process(data)
    }
    case _ => log.info("received unknown message")
  }
}


object WorkerMessages extends Data {
  case class Process(data:DataType)
}

object WorkerActor {
  def apply(writer:ActorRef) = new WorkerActor(writer:ActorRef)
}
class WorkerActor(writer:ActorRef) extends Actor {
  import WorkerMessages._
  import WriterMessages._

  val log = Logging(context.system, this)

  def work(data:DataType): DataType = {
    (0 to 100).foreach( (i) => (0 to i*i).foreach( (i2) => i*i2))
    writer ! Write(data)
    data
  }

  def receive = {
    case Process(data) => {
      log.info("working on data: %s" format data)
      work(data)
    }
    case _ => log.info("received unknown message")
  }
}


object WriterMessages extends Data {
  case class Write(data:DataType)
}

object WriteActor {
  def apply() = new WriteActor()
}
class WriteActor extends Actor {
  import WriterMessages._
  val log = Logging(context.system, this)
  context.setReceiveTimeout(5 second)


  def receive = {
    case Write(data) => {
      log.info("writing data %s" format data)
    }
    case ReceiveTimeout => {
      //context.setReceiveTimeout(Duration.Undefined)
      log.info("no more data received, shutting down!")
      context.system.shutdown()
    }
    case _ => log.info("received unknown message")
  }
}


trait CreationPolicy {
  val readers: Int = 2
  val workers: Int = 6
  val writers: Int = 1
  val router = SmallestMailboxRouter
}

object Messages {
  case class Start()
  case class Stop()
}

class StreamerActor extends Actor with CreationPolicy {
  import Messages._
  import ReaderMessages._

  val log = Logging(context.system, this)

  val writer = context.actorOf(Props(WriteActor()).withRouter(
    router(nrOfInstances = writers)
  ), "writer")

  val worker = context.actorOf(Props(WorkerActor(writer)).withRouter(
    router(nrOfInstances = workers)
  ), "worker")

  val reader = context.actorOf(Props(ReadActor(worker)).withRouter(
    router(nrOfInstances = readers)
  ), "reader")

  def receive = {
    case Start() => {
      log.info("Started")
      DB.getData.foreach{ (data) =>
        reader ! Read(data)
      }

    }
    case Stop() => {
      log.info("Shutdown")
      context.system.shutdown()
    }
  }
}

object DB extends Data {
  val dataLimit:DataType = 500
  val data:DataStream = (1 to dataLimit).toStream

  def getData:DataStream = data
}

object Main extends App {
  import Messages._

  println("Started...")
  val system = ActorSystem("Streamer")
  val streamerActor = system.actorOf(Props[StreamerActor].withRouter(
    SmallestMailboxRouter(nrOfInstances = 1)
  ), name="streamerActor")

  streamerActor ! Start()
//  Thread.sleep(20000)
//  streamerActor ! Stop()
//  Thread.sleep(1000)
//  system.shutdown()
}
