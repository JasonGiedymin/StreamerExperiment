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

package com.jasongiedymin.concurrent.v2

/**
 * Warning: Use Scala 2.10 only, and make sure to do sbt clean very
 * often...
 *
 * No Blocking Allocator, fully streaming with thread pools
 */

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Try
import java.util.concurrent.Executors


/**
 * Generic And Virtual Types
 * @tparam A The Generic Type Trait to be mixed in
 */
trait Data[A] {
  type DataType = A
  type Possible = Option[DataType]
  type InputStream = Stream[DataType]
  type FutureData = Future[Possible]
  type FutureOutput = Stream[FutureData]
  type FutureTry = Try[Possible]
  type FutureValue = Option[FutureTry]
  type FutureSequence = Future[InputStream]

  type Timing = (Long, Long)
}

/**
 * Generic Type Container
 * @tparam A The Generic Type to be mixed in which defines the base
 *           type of the value contained within.
 */
trait DataContainer[A] extends Data[A] {
  val value:DataType
  def getValue: DataType = value
}

/**
 * Generic data generator
 * @tparam A An Generic Type to be mixed in which defines the base
 *           type a Generator will create or "generate".
 */
trait Generator[A] extends Data[A] {
  def generate:InputStream
}

trait FrameworkSettings {

  /**
   * Time for warming up the framework
   * @return
   */
  def warmupTime:Option[Duration] = Some(10.second) //or Null //impl can override

  /**
   * Used to warmup the framework
   */
  def warmup() {
    warmupTime match {
      case Some(time) => {
        println("* Warming up...")
        Thread.sleep(time.toMillis)
      }
      case None => println("No warmup specified, proceeding with processing...")
    }
  }

  // !! Thinking of overriding values??? Do it in the Impl instead.
  // Workers pass on work to Writers
  // Sharing pools will force blocking
  // Seperate pools are necessary
  val osReserve:Int = 1
  val jvmReserve:Int = 1
  val reserveCPUs:Int = osReserve + jvmReserve
  val systemCPUs = Runtime.getRuntime.availableProcessors()
  val appCPUs = systemCPUs - reserveCPUs
  val workerSaturation:Double = .5
  def workers:Int = (appCPUs * workerSaturation).toInt // !! Thinking of overriding values??? Do it in the Impl instead.
  def successors:Int = appCPUs - workers // lower this number to see the queue build up
  def workerTimeout:Duration = Duration.Inf //1.second // work must complete within this time
  //def successorTimeout:Duration = Duration.Inf //1.second // successor must complete within this time
  def appTimeout:Duration = Duration.Inf // app timeout

  var ecWorkers = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(workers))
  var ecSuccessors = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(successors))

  //reserved
  //var ecApp = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(1)) // dedicated for app

  def shutdown() {
    ecSuccessors.shutdown()
    ecWorkers.shutdown()
    //ecApp.shutdown()
  }

}

trait Worker[A] extends Data[A] with FrameworkSettings {
  def work(input:DataType): Possible
  def complete(value:DataType): Possible

  protected def success(result:FutureData): Possible = {
    // We still have a streaming promise at this point
    // make sure work is actually complete & DONE
    Await.result(result, workerTimeout)

    import java.lang.management._
    import com.sun.management.OperatingSystemMXBean
    val systemTime = System.nanoTime()
    val availableProcessors = appCPUs

    def timing:Timing = {
      val systemTime = System.nanoTime()
      val lastProcessCpuTime, lastSystemTime = 0

      val processCpuTime = (ManagementFactory.getOperatingSystemMXBean.asInstanceOf[OperatingSystemMXBean]).getProcessCpuTime
      (processCpuTime, systemTime)
    }

    def busyCPUs(start:Timing, end:Timing) = (end._1 - start._1) / (end._2 - systemTime)

    val ret = result.value match {
      case Some(t) => {
        val start = timing
        val blockingResult = blocking {
          complete(t.get.get)
        }
        val end = timing
        val usage = busyCPUs(start, end)
        if (usage < workers)
          println("==== [ %s/%s CPUs Utilized ] ==== - consider increasing successors thread count to match workers. If at tail end of data, ignore this suggestion" format (usage,availableProcessors))

        blockingResult
      }
      case None => {
        println("!!! Error !!! - couldn't retreive value from %s" format result)
        None
      }
    }

    ret
  }
}

abstract class Framework[A] extends FrameworkSettings with Worker[A] {
  def distribute(inputStream:InputStream)(implicit ec: ExecutionContext = ecWorkers): FutureOutput = {
    warmup()

    lazy val ret = for ( i<-inputStream ) yield {
      println("-> promising data: %s" format i)
      future { work(i) }
      // The reason we don't do the following is success() will
      // end up sharing work()'s thread pool and may be scheduled
      // much much later.
      // andThen {
        //case _ => success(_) // naive match
      //}
    }
    ret
  }

  /**
   * http://www.playframework.com/documentation/2.1.0/ThreadPools
   * "Note that you may be tempted to therefore wrap your blocking
   * code in Futures. This does not make it non blocking, it just
   * means the blocking will happen in a different thread. You
   * still need to make sure that the thread pool that you are
   * using there has enough threads to handle the blocking."
   */
  def collect(result:FutureOutput)(implicit ec: ExecutionContext = ecSuccessors): FutureOutput = {
    /**
     * We still have a stream, we can do something
     * custom in the yield if need be, otherwise
     * pass it along
     */
    lazy val next = for {
      item <- result
    } yield { future(success(item)) } // we return back identity, but we could yield more

    Await.result(Future.sequence(next), appTimeout) // block
    next
  }
}


/**
 * ------------------------------------------------------------------
 */

/**
 * Example Usage of the Generic Type Container with Int data
 * This is our data container.
 * @param value is the actual value and type you wish to store
 */
class IntegerData(val value:Int) extends DataContainer[Int] {}

/**
 * Example Usage of Generic Generator Companion.
 * This is our test data Generator.
 */
object IntegerData extends Generator[Int] {
  /**
   * function to generate test data as a stream
   * to simulate infinite data
   * seemingly infinite, but definitely, finite :-)
   *
   * Not recommended, as it will always seem sequential due to worker
   * using n*1000
   * def generate = (1 to 10).toStream
   *
   * A good basic test to prove 1s execute first and 5 later
   * def generate = Stream(5, 1, 1, 1)
   */
   def generate = (1 to 50)./*reverse.*/toStream
}

/**
 * Example Usage of Generic Worker trait.
 * This is our Fake worker that does "work".
 * It also defines a callback success method.
 */
trait FakeIntegerWorker extends Worker[Int] {
  def work(input:DataType): Possible = {
    (1 to 500).foreach( (i) => (1 to i*i).foreach( (i2) => i*i2))
    println("  ! Work was done for int: %s" format input)
    Some(input)
  }

  def complete(input:DataType): Possible = {
    println("  !! Simulating long success op %s" format input)
    // Thread.sleep(result * 100) OR
    (1 to 500).foreach( (i) => (1 to i*i).foreach( (i2) => i*i2))
    println("<- Complete on %s!" format input)
    Some(input)
  }

}

/**
 * Example Usage of Generic Framework Companion with Worker mixed in.
 * This is our framework which will execute workers.
 */
object IntegerFramework extends Framework[Int] with FakeIntegerWorker {
  // override any options here such as timeouts and threads
  override def warmupTime = Some(1.second) //or None
  //override def workers:Int = (Runtime.getRuntime.availableProcessors() * .5 ).toInt - 1
  //override def successors:Int = Runtime.getRuntime.availableProcessors() - workers
  override def workerTimeout:Duration = Duration.Inf
  override def appTimeout:Duration = Duration.Inf

  def status() {
    println("System CPUS: %s" format systemCPUs)
    println("App CPUS: %s" format appCPUs)
    println("Reserve CPUS (OS + JVM): %s" format reserveCPUs)
    println("  worker saturation: [%s]" format workerSaturation)
    println("  workers: [%s]" format workers)
    println("  successors: [%s]" format successors)
    println("  worker timeout: [%s]" format workerTimeout)
    println("  app timeout: [%s]" format appTimeout)
  }
}

/**
 * ------------------------------------------------------------------
 */

object Streamer {
  def start() {
    IntegerFramework.status()

    lazy val data = IntegerData.generate // generate data
    println("Generated Data: %s" format data) // prove it is data

    lazy val promises = IntegerFramework.distribute(data) // generate promises
    println("Promised result: %s" format promises) // prove that the promise are streamed

    // Call to collect promises and will execute callbacks.
    IntegerFramework.collect(promises)

    IntegerFramework.shutdown()
  }

}

class Streamer {}

object Main extends App {
  println("Started")
  Streamer.start()
}