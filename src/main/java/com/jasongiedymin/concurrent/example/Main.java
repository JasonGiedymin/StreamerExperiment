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

package com.jasongiedymin.concurrent.example;

import scala.Option;
import scala.collection.immutable.Stream;
import scala.concurrent.ExecutionContextExecutorService;
import scala.concurrent.Future;


public class Main {
    public static void main(String[] args) {

        MyDataFramework framework = new MyDataFramework();
        ExecutionContextExecutorService workers = framework.ecWorkers();
        ExecutionContextExecutorService successors = framework.ecSuccessors();

        framework.status();
        Stream<MyData> input = new MyDataGenerator().generate();
        Stream<Future<Option<MyData>>> output = framework.distribute(input, workers);
        framework.collect( output, successors );

        framework.shutdown();
    }
}
