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

package com.jasongiedymin.concurrent.v1

/**
 * Warning: Use Scala 2.10 only, and make sure to do sbt clean very
 * often...
 *
 * Blocking Allocator
 */

import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Try

/**
 * Generic And Virtual Types
 * @tparam A The Generic Type Trait to be mixed in
 */
trait Data[A] {
  type DataType = A
  type InputStream = Stream[DataType]
  type FutureData = Future[A]
  type FutureOutput = Stream[FutureData]
  type FutureValue = Option[Try[DataType]]
  type FutureSequence = Future[Stream[DataType]]
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
  def workerTimeout:Duration = Duration.Inf // can be used for individual work
  def appTimeout:Duration = Duration.Inf
}

trait Worker[A] extends Data[A] with FrameworkSettings {
  def work(input:DataType):DataType
  def success(value:FutureData):DataType
}

abstract class Framework[A] extends FrameworkSettings with Worker[A] {
  def distribute(inputStream:InputStream):FutureOutput = {
    lazy val dist = for ( i<-inputStream ) yield {
      println("-> promising data: %s" format i)
      future(work(i))
    }
    dist.par.toStream
  }

  def collect(result:FutureOutput) {
    lazy val work = Future.traverse(result)( (futureItem) => Future(success(futureItem)))
    Await.result(work, appTimeout)
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
   */
  // Not recommended, as it will always seem sequential due to worker
  // using n*1000
  //def generate = (0 to 10).toStream

  // A good basic test to prove 1s execute first and 5 later
  //def generate = Stream(5, 1, 1, 1)

  // Optimum test which shows first come first serve nature of par
  def generate = {
    val ret = (1 to 500).reverse.toStream
    ret
  }
}

/**
 * Example Usage of Generic Worker trait.
 * This is our Fake worker that does "work".
 * It also defines a callback success method.
 */
trait FakeIntegerWorker extends Worker[Int] {
  def work(input:Int): DataType = {
    //Thread.sleep(input * 100)
    (1 to 500).foreach( (i) => (1 to i*i).foreach( (i2) => i*i2))
    println("  ! Work was done for int: %s" format input)
    input
  }

  def success(value:FutureData): DataType = {
    lazy val result = value.value.get.get
    println("  !! Simulating long success op %s" format result)
    (1 to 500).foreach( (i) => (1 to i*i).foreach( (i2) => i*i2))
    println("<- Complete on %s!" format result)
    result
  }
}

/**
 * Example Usage of Generic Framework Companion with Worker mixed in.
 * This is our framework which will execute workers.
 */
object IntegerFramework extends Framework[Int] with FakeIntegerWorker {}

/**
 * ------------------------------------------------------------------
 */

object Streamer {
  def start() {
    lazy val data = IntegerData.generate // generate data
    println("Generated Data: %s" format data) // prove it is data

    lazy val promises = IntegerFramework.distribute(data) // generate promises
    println("Promised result: %s" format promises) // prove that the promise are streamed

    // Call to collect promises and will execute callbacks.
    IntegerFramework.collect(promises)
  }

}

class Streamer {}

object Main extends App {
  println("Started")
  Streamer.start()
}