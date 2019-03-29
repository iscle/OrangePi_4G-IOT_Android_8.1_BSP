/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.compatibility.common.tradefed.testtype;

import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.util.xml.AbstractXmlParser.ParseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;


/**
 * Interface for generating, parsing, and retrieving subplan data.
 */
public interface ISubPlan extends ITestFilterReceiver {

    /**
     * Parse the subplan data from a stream of XML, populate collections of filters internally.
     * @param xmlInputStream the {@link InputStream} containing subplan XML
     */
    public void parse(InputStream xmlInputStream) throws ParseException;

    /**
     * Retrieve the set of include filters previously added or parsed from XML.
     * @return a set of include filter strings
     */
    public Set<String> getIncludeFilters();

    /**
     * Retrieve the set of exclude filters previously added or parsed from XML.
     * @return a set of exclude filter strings
     */
    public Set<String> getExcludeFilters();

    /**
     * Serialize the existing filters into a stream of XML, and write to an output stream.
     * @param xmlOutputStream the {@link OutputStream} to receive subplan XML
     */
    public void serialize(OutputStream xmlOutputStream) throws IOException;
}
