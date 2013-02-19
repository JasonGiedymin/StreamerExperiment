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

import com.jasongiedymin.concurrent.v2.Generator;
import com.jasongiedymin.concurrent.v2.GeneratorHelper;
import scala.collection.immutable.Stream;

import java.util.ArrayList;
import java.util.List;

public class MyDataGenerator extends GeneratorHelper<MyData> implements Generator<MyData> {

    @Override
    public Stream<MyData> generate() {
        List<MyData> data = new ArrayList<MyData>();

        // Generate 1000 custom 'MyData' elements
        for(int i=1; i<1000; i++) {
            MyData tempData = new MyData(i);
            System.out.println(String.format("Generated data: %s", tempData.value()));
            data.add(tempData);
        }

        return generateHelper(data);
    }

}
