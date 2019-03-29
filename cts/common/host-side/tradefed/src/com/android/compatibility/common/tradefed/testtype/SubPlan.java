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

import com.android.compatibility.common.util.TestFilter;
import com.android.tradefed.util.xml.AbstractXmlParser;

import org.kxml2.io.KXmlSerializer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Container, parser, and generator of SubPlan info.
 */
public class SubPlan extends AbstractXmlParser implements ISubPlan {

    private final Set<String> mIncludes;
    private final Set<String> mExcludes;

    private static final String ENCODING = "UTF-8";
    private static final String NS = null; // namespace used for XML serializer
    private static final String VERSION_ATTR = "version";
    private static final String SUBPLAN_VERSION = "2.0";

    private static final String SUBPLAN_TAG = "SubPlan";
    private static final String ENTRY_TAG = "Entry";
    private static final String EXCLUDE_ATTR = "exclude";
    private static final String INCLUDE_ATTR = "include";
    private static final String ABI_ATTR = "abi";
    private static final String NAME_ATTR = "name";

    public SubPlan() {
        mIncludes = new HashSet<String>();
        mExcludes = new HashSet<String>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludes.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllIncludeFilters(Set<String> filters) {
        mIncludes.addAll(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludes.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllExcludeFilters(Set<String> filters) {
        mExcludes.addAll(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getIncludeFilters() {
        return new HashSet<String>(mIncludes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getExcludeFilters() {
        return new HashSet<String>(mExcludes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(OutputStream stream) throws IOException {
        KXmlSerializer serializer = new KXmlSerializer();
        serializer.setOutput(stream, ENCODING);
        serializer.startDocument(ENCODING, false);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.startTag(NS, SUBPLAN_TAG);
        serializer.attribute(NS, VERSION_ATTR, SUBPLAN_VERSION);

        ArrayList<String> sortedIncludes = new ArrayList<String>(mIncludes);
        ArrayList<String> sortedExcludes = new ArrayList<String>(mExcludes);
        Collections.sort(sortedIncludes);
        Collections.sort(sortedExcludes);
        for (String include : sortedIncludes) {
            serializer.startTag(NS, ENTRY_TAG);
            serializer.attribute(NS, INCLUDE_ATTR, include);
            serializer.endTag(NS, ENTRY_TAG);
        }
        for (String exclude : sortedExcludes) {
            serializer.startTag(NS, ENTRY_TAG);
            serializer.attribute(NS, EXCLUDE_ATTR, exclude);
            serializer.endTag(NS, ENTRY_TAG);
        }

        serializer.endTag(NS, SUBPLAN_TAG);
        serializer.endDocument();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DefaultHandler createXmlHandler() {
        return new EntryHandler();
    }

    /**
     * SAX callback object. Handles parsing data from the xml tags.
     */
    private class EntryHandler extends DefaultHandler {

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes)
                throws SAXException {
            if (ENTRY_TAG.equals(localName)) {
                String includeString = attributes.getValue(INCLUDE_ATTR);
                String excludeString = attributes.getValue(EXCLUDE_ATTR);
                if (includeString != null && excludeString != null) {
                    throw new IllegalArgumentException(
                            "Cannot specify include and exclude filter in the same element");
                }
                String abiString = attributes.getValue(ABI_ATTR);
                String nameString = attributes.getValue(NAME_ATTR);

                if (excludeString == null) {
                    parseFilter(abiString, nameString, includeString, mIncludes);
                } else {
                    parseFilter(abiString, nameString, excludeString, mExcludes);
                }
            }
        }

        private void parseFilter(String abi, String name, String filter, Set<String> filterSet) {
            if (name == null) {
                // ignore name and abi attributes, 'filter' should contain all necessary parts
                filterSet.add(filter);
            } else {
                // 'filter' is name of test. Build TestFilter and convert back to string
                filterSet.add(new TestFilter(abi, name, filter).toString());
            }
        }
    }
}
