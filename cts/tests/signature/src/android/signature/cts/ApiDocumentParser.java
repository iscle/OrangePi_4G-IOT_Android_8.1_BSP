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
package android.signature.cts;

import static android.signature.cts.CurrentApi.ATTRIBUTE_NAME;
import static android.signature.cts.CurrentApi.ATTRIBUTE_TYPE;
import static android.signature.cts.CurrentApi.TAG_CLASS;
import static android.signature.cts.CurrentApi.TAG_CONSTRUCTOR;
import static android.signature.cts.CurrentApi.TAG_EXCEPTION;
import static android.signature.cts.CurrentApi.TAG_FIELD;
import static android.signature.cts.CurrentApi.TAG_IMPLEMENTS;
import static android.signature.cts.CurrentApi.TAG_INTERFACE;
import static android.signature.cts.CurrentApi.TAG_METHOD;
import static android.signature.cts.CurrentApi.TAG_PACKAGE;
import static android.signature.cts.CurrentApi.TAG_PARAM;
import static android.signature.cts.CurrentApi.TAG_ROOT;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Parses an XML api definition file and constructs and populates an {@link JDiffClassDescription}
 * for every class.
 *
 * <p>Once it has completely populated the members (so does not include nested/inner classes) of a
 * {@link JDiffClassDescription} it notifies the {@link #listener} by calling
 * {@link Listener#completedClass(JDiffClassDescription)} with the completed
 * {@link JDiffClassDescription}.
 */
public class ApiDocumentParser {

    private static final Set<String> KEY_TAG_SET;
    static {
        KEY_TAG_SET = new HashSet<>();
        Collections.addAll(KEY_TAG_SET,
                TAG_PACKAGE,
                TAG_CLASS,
                TAG_INTERFACE,
                TAG_IMPLEMENTS,
                TAG_CONSTRUCTOR,
                TAG_METHOD,
                TAG_PARAM,
                TAG_EXCEPTION,
                TAG_FIELD);
    }

    private final String tag;

    private final Listener listener;

    private final XmlPullParser parser;

    public ApiDocumentParser(String tag, Listener listener) throws XmlPullParserException {
        this.tag = tag;
        this.listener = listener;

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        parser = factory.newPullParser();
    }

    public void parse(InputStream inputStream) throws XmlPullParserException, IOException {
        parser.setInput(inputStream, null);
        start(parser);
    }

    public interface Listener {

        /**
         * Invoked when a {@link JDiffClassDescription} has been completely populated.
         *
         * @param classDescription the description of the class as read from the XML API file.
         */
        void completedClass(JDiffClassDescription classDescription);
    }


    private void beginDocument(XmlPullParser parser, String firstElementName)
            throws XmlPullParserException, IOException {
        int type;
        do {
            type = parser.next();
        } while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT);

        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!parser.getName().equals(firstElementName)) {
            throw new XmlPullParserException("Unexpected start tag: found " + parser.getName() +
                    ", expected " + firstElementName);
        }
    }

    /**
     * Signature test entry point.
     */
    private void start(XmlPullParser parser) throws XmlPullParserException, IOException {
        logd(String.format("Name: %s", parser.getName()));
        logd(String.format("Text: %s", parser.getText()));
        logd(String.format("Namespace: %s", parser.getNamespace()));
        logd(String.format("Line Number: %s", parser.getLineNumber()));
        logd(String.format("Column Number: %s", parser.getColumnNumber()));
        logd(String.format("Position Description: %s", parser.getPositionDescription()));
        JDiffClassDescription currentClass = null;
        String currentPackage = "";
        JDiffClassDescription.JDiffMethod currentMethod = null;

        beginDocument(parser, TAG_ROOT);
        int type;
        while (true) {
            do {
                type = parser.next();
            } while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.END_TAG);

            if (type == XmlPullParser.END_TAG) {
                if (TAG_CLASS.equals(parser.getName())
                        || TAG_INTERFACE.equals(parser.getName())) {
                    if (listener != null) {
                        listener.completedClass(currentClass);
                    }
                } else if (TAG_PACKAGE.equals(parser.getName())) {
                    currentPackage = "";
                }
                continue;
            }

            if (type == XmlPullParser.END_DOCUMENT) {
                break;
            }

            String tagname = parser.getName();
            if (!KEY_TAG_SET.contains(tagname)) {
                continue;
            }

            if (type == XmlPullParser.START_TAG && tagname.equals(TAG_PACKAGE)) {
                currentPackage = parser.getAttributeValue(null, ATTRIBUTE_NAME);
            } else if (tagname.equals(TAG_CLASS)) {
                currentClass = CurrentApi.loadClassInfo(
                        parser, false, currentPackage);
            } else if (tagname.equals(TAG_INTERFACE)) {
                currentClass = CurrentApi.loadClassInfo(
                        parser, true, currentPackage);
            } else if (tagname.equals(TAG_IMPLEMENTS)) {
                currentClass.addImplInterface(parser.getAttributeValue(null, ATTRIBUTE_NAME));
            } else if (tagname.equals(TAG_CONSTRUCTOR)) {
                JDiffClassDescription.JDiffConstructor constructor =
                        CurrentApi.loadConstructorInfo(parser, currentClass);
                currentClass.addConstructor(constructor);
                currentMethod = constructor;
            } else if (tagname.equals(TAG_METHOD)) {
                currentMethod = CurrentApi.loadMethodInfo(currentClass.getClassName(), parser);
                currentClass.addMethod(currentMethod);
            } else if (tagname.equals(TAG_PARAM)) {
                currentMethod.addParam(parser.getAttributeValue(null, ATTRIBUTE_TYPE));
            } else if (tagname.equals(TAG_EXCEPTION)) {
                currentMethod.addException(parser.getAttributeValue(null, ATTRIBUTE_TYPE));
            } else if (tagname.equals(TAG_FIELD)) {
                JDiffClassDescription.JDiffField field = CurrentApi.loadFieldInfo(currentClass.getClassName(), parser);
                currentClass.addField(field);
            } else {
                throw new RuntimeException(
                        "unknown tag exception:" + tagname);
            }
            if (currentPackage != null) {
                logd(String.format("currentPackage: %s", currentPackage));
            }
            if (currentClass != null) {
                logd(String.format("currentClass: %s", currentClass.toSignatureString()));
            }
            if (currentMethod != null) {
                logd(String.format("currentMethod: %s", currentMethod.toSignatureString()));
            }
        }
    }

    private void logd(String msg) {
        Log.d(tag, msg);
    }
}
