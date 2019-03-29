/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.List;

/**
 * {@link DefaultHander} that parses the output of dexdeps and adds the coverage information to
 * an {@link ApiCoverage} object.
 */
class DexDepsXmlHandler extends DefaultHandler {

    private final ApiCoverage mPackageMap;

    private final String mTestApkName;

    private String mCurrentPackageName;

    private String mCurrentClassName;

    private String mCurrentMethodName;

    private String mCurrentMethodReturnType;

    private List<String> mCurrentParameterTypes = new ArrayList<String>();

    DexDepsXmlHandler(ApiCoverage packageMap, String testApkName) {
        this.mPackageMap = packageMap;
        this.mTestApkName = testApkName;
    }

    @Override
    public void startElement(String uri, String localName, String name, Attributes attributes)
            throws SAXException {
        super.startElement(uri, localName, name, attributes);
        if ("package".equalsIgnoreCase(localName)) {
            mCurrentPackageName = CurrentXmlHandler.getValue(attributes, "name");
        } else if ("class".equalsIgnoreCase(localName)
                || "interface".equalsIgnoreCase(localName)) {
            mCurrentClassName = CurrentXmlHandler.getValue(attributes, "name");
        } else if ("constructor".equalsIgnoreCase(localName)) {
            mCurrentParameterTypes.clear();
        }  else if ("method".equalsIgnoreCase(localName)) {
            mCurrentMethodName = CurrentXmlHandler.getValue(attributes, "name");
            mCurrentMethodReturnType = CurrentXmlHandler.getValue(attributes, "return");
            mCurrentParameterTypes.clear();
        } else if ("parameter".equalsIgnoreCase(localName)) {
            mCurrentParameterTypes.add(CurrentXmlHandler.getValue(attributes, "type"));
        }
    }

    @Override
    public void endElement(String uri, String localName, String name) throws SAXException {
        super.endElement(uri, localName, name);
        if ("constructor".equalsIgnoreCase(localName)) {
            ApiPackage apiPackage = mPackageMap.getPackage(mCurrentPackageName);
            if (apiPackage != null) {
                ApiClass apiClass = apiPackage.getClass(mCurrentClassName);
                if (apiClass != null) {
                    apiClass.markConstructorCovered(mCurrentParameterTypes, mTestApkName);
                }
            }
        }  else if ("method".equalsIgnoreCase(localName)) {
            ApiPackage apiPackage = mPackageMap.getPackage(mCurrentPackageName);
            if (apiPackage != null) {
                ApiClass apiClass = apiPackage.getClass(mCurrentClassName);
                if (apiClass != null) {
                    apiClass.markMethodCovered(
                            mCurrentMethodName, mCurrentParameterTypes, mCurrentMethodReturnType,
                                    mTestApkName);
                }
            }
        }
    }
}
