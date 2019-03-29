/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.apicoverage;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * {@link DefaultHandler} that builds an empty {@link ApiCoverage} object from scanning
 * TestModule.xml.
 */
class TestModuleConfigHandler extends DefaultHandler {
    private String mTestClassName;
    private String mModuleName;
    private Boolean inTestEle = false;

    @Override
    public void startElement(String uri, String localName, String name, Attributes attributes)
            throws SAXException {
        super.startElement(uri, localName, name, attributes);

        if ("test".equalsIgnoreCase(localName)) {
            mTestClassName = attributes.getValue("class");
            inTestEle = true;
        } else if ("option".equalsIgnoreCase(localName)) {
            if (inTestEle) {
                String optName = attributes.getValue("name");
                if ("module-name".equalsIgnoreCase(optName)) {
                    mModuleName = attributes.getValue("value");
                }
                //System.out.println(String.format("%s: %s, %s, %s", localName, name, optName, attributes.getValue("value")));
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {
        super.endElement(uri, localName, name);
        if ("test".equalsIgnoreCase(localName)) {
            inTestEle = false;
        }
    }

    public String getModuleName() {
        return mModuleName;
    }

    public String getTestClassName() {
        return mTestClassName;
    }
}
