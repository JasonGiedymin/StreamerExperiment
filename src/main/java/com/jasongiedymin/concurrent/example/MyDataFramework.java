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

import com.jasongiedymin.concurrent.v2.Framework;
import com.jasongiedymin.concurrent.v2.Worker;
import scala.Option;
import scala.concurrent.duration.Duration;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

public class MyDataFramework extends Framework<MyData> implements Worker<MyData> {

    @Override
    public Option<Duration> warmupTime(){
        return Option.apply(Duration.apply(2.0, TimeUnit.SECONDS));
    }

    @Override
    public int jvmReserve() {
        return 0; // not doing anything which requires lots of memory to hang around
    }

    @Override
    public double workerSaturation() {
        return .9; // work is cpu intensive, lower this if successors need more cpu
    }

    @Override
    public int successors() {
        return 1;
    }

    @Override
    public Option<MyData> work(MyData data) {
        // doing some meaningful (fake) work here with 'MyData'
        System.out.println(String.format("-> working on: %s", data.value()));
        for(int i=0; i<100; i++) {
            BigInteger x = new BigInteger("1421309482938471293048719283749127394712347123974190283747234890723904878129347901237490123740987329470327409587239075907324057890342757347071032047012739047012374890721390470912374907123904791237497234239847093274097315");
            BigInteger y = x.multiply(x).pow(100).negate();
        }

        return Option.apply(data);
    }

    @Override
    public Option<MyData> complete(MyData data) {
        System.out.println( String.format("<- complete: %s", data.value()) );
        return Option.apply(data);
    }
}
