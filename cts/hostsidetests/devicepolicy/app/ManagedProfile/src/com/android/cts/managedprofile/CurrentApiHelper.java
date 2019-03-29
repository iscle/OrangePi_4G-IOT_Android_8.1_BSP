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

package com.android.cts.managedprofile;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Primitives;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.lang.reflect.Method;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Helper class for retrieving the current list of API methods.
 */
public class CurrentApiHelper {

    /**
     * Location of the XML file that lists the current public APIs.
     *
     * <p><b>Note:</b> must be consistent with
     * {@code cts/hostsidetests/devicepolicy/AndroidTest.xml}
     */
    private static final String CURRENT_API_FILE = "/data/local/tmp/device-policy-test/current.api";

    private static final String LOG_TAG = "CurrentApiHelper";

    private static final ImmutableMap<String, Class> PRIMITIVE_TYPES = getPrimitiveTypes();
    private static final ImmutableMap<String, String> PRIMITIVE_ENCODINGS =
            new ImmutableMap.Builder<String, String>()
                    .put("boolean", "Z")
                    .put("byte", "B")
                    .put("char", "C")
                    .put("double", "D")
                    .put("float", "F")
                    .put("int", "I")
                    .put("long", "J")
                    .put("short", "S")
                    .build();

    private static final String TAG_PACKAGE = "package";
    private static final String TAG_CLASS = "class";
    private static final String TAG_METHOD = "method";
    private static final String TAG_PARAMETER = "parameter";

    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_TYPE = "type";

    /**
     * Get public API methods of a specific class as defined in the API document.
     *
     * @param packageName The name of the package containing the class, e.g. {@code android.app}.
     * @param className The name of the class, e.g. {@code Application}.
     * @return an immutable list of {@link Method} instances.
     */
    public static ImmutableList<Method> getPublicApis(String packageName, String className)
            throws Exception {
        Document apiDocument = parseXmlFile(CURRENT_API_FILE);
        Element rootElement = apiDocument.getDocumentElement();
        Element packageElement = getChildElementByName(rootElement, TAG_PACKAGE, packageName);
        Element classElement = getChildElementByName(packageElement, TAG_CLASS, className);

        ImmutableList.Builder<Method> builder = new ImmutableList.Builder<>();

        NodeList nodes = classElement.getElementsByTagName(TAG_METHOD);
        if (nodes != null && nodes.getLength() > 0) {
            Class clazz = Class.forName(packageName + "." + className);

            for (int i = 0; i < nodes.getLength(); ++i) {
                Element element = (Element) nodes.item(i);
                String name = element.getAttribute(ATTRIBUTE_NAME);
                Class[] paramTypes = getParamTypes(element);
                builder.add(clazz.getMethod(name, paramTypes));
            }
        }

        return builder.build();
    }

    /**
     * Given a {@link Class} object, get the default value if the {@link Class} refers to a
     * primitive type, or null if it refers to an object.
     *
     * <p><ul>
     *     <li>For boolean type, return {@code false}
     *     <li>For other primitive types, return {@code 0}
     *     <li>For all other types, return {@code null}
     * </ul>
     * @param clazz The desired class to instantiate.
     * @return Default instance as described above.
     */
    public static Object instantiate(Class clazz) {
        if (clazz.isPrimitive()) {
            if (boolean.class.equals(clazz)) {
                return false;
            } else {
                return 0;
            }
        } else {
            return null;
        }
    }

    private static Document parseXmlFile(String filePath) throws Exception {
        File apiFile = new File(filePath);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document dom = db.parse(apiFile.toURI().toString());

        return dom;
    }

    private static Element getChildElementByName(Element parent,
            String childTag, String childName) {
        NodeList nodeList = parent.getElementsByTagName(childTag);
        if (nodeList != null && nodeList.getLength() > 0) {
            for (int i = 0; i < nodeList.getLength(); ++i) {
                Element el = (Element) nodeList.item(i);
                if (childName.equals(el.getAttribute(ATTRIBUTE_NAME))) {
                    return el;
                }
            }
        }
        return null;
    }

    private static Class[] getParamTypes(Element methodElement) throws Exception {
        NodeList nodes = methodElement.getElementsByTagName(TAG_PARAMETER);
        if (nodes != null && nodes.getLength() > 0) {
            int paramCount = nodes.getLength();
            Class[] paramTypes = new Class[paramCount];
            for (int i = 0; i < paramCount; ++i) {
                String typeName = ((Element) nodes.item(i)).getAttribute(ATTRIBUTE_TYPE);
                paramTypes[i] = getClassByName(typeName);
            }
            return paramTypes;
        } else {
            return new Class[0];
        }
    }

    private static Class getClassByName(String typeName) throws ClassNotFoundException {
        // Check if typeName represents an array
        int arrayDim = 0;
        while (typeName.endsWith("[]")) {
            arrayDim++;
            typeName = typeName.substring(0, typeName.length() - 2);
        }

        // Remove type parameters, if any
        typeName = typeName.replaceAll("<.*>$", "");

        if (arrayDim == 0) {
            if (isPrimitiveTypeName(typeName)) {
                return PRIMITIVE_TYPES.get(typeName);
            } else {
                return Class.forName(typeName);
            }

        } else {
            String prefix = Strings.repeat("[", arrayDim);
            if (isPrimitiveTypeName(typeName)) {
                return Class.forName(prefix + PRIMITIVE_ENCODINGS.get(typeName));
            } else {
                return Class.forName(prefix + "L" + typeName + ";");
            }
        }
    }

    private static ImmutableMap<String, Class> getPrimitiveTypes() {
        ImmutableMap.Builder<String, Class> builder = new ImmutableMap.Builder<>();
        for (Class type : Primitives.allPrimitiveTypes()) {
            builder.put(type.getName(), type);
        }
        return builder.build();
    }

    private static boolean isPrimitiveTypeName(String typeName) {
        return PRIMITIVE_TYPES.containsKey(typeName);
    }
}
