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

import com.android.compatibility.common.util.ReadElf;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
/**
 * Class that outputs an XML report of the {@link ApiCoverage} collected. It can be viewed in a
 * browser when used with the api-coverage.css and api-coverage.xsl files.
 */
class NdkApiXmlReport {
    private static final String API_TAG = "api";
    private static final String PACKAGE_TAG = "package";
    private static final String CLASS_TAG = "class";
    private static final String METHOD_TAG = "method";
    private static final String FIELD_TAG = "field";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String NDK_PACKAGE_NAME = "ndk";
    private static final String NDK_DUMMY_RETURN_TYPE = "na";

    private static final Map<String, String> sInternalSymMap;
    static {
        sInternalSymMap = new HashMap<String, String>();
        sInternalSymMap.put("__bss_start", "bss");
        sInternalSymMap.put("_end", "initialized data");
        sInternalSymMap.put("_edata", "uninitialized data");
    }

    private static final FilenameFilter SUPPORTED_FILE_NAME_FILTER =
            new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    String fileName = name.toLowerCase();
                    return fileName.endsWith(".so");
                }
            };

    private static void printUsage() {
        System.out.println("Usage: ndk-api-xml-report [OPTION]... [APK]...");
        System.out.println();
        System.out.println("Generates a report about what Android NDK methods.");
        System.out.println();
        System.out.println("this must be used from the $ANDROID_BUILD_TOP");
        System.out.println("make cts-test-coverage");
        System.out.println("unzip the target ndk_platform.tar.bz2 to a folder.");
        System.out.println(
                "$ANDROID_HOST_OUT/bin/ndk-api-report "
                        + "-o $ANDROID_BUILD_TOP/cts/tools/cts-api-coverage/etc/ndk-api.xml "
                        + "-n <ndk-folder>/platforms/android-current/arch-arm64/usr/lib");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -o FILE                output file or standard out if not given");
        System.out.println("  -n PATH                path to the NDK Lib Folder");
        System.out.println();
        System.exit(1);
    }

    /** Get the argument or print out the usage and exit. */
    private static String getExpectedArg(String[] args, int index) {
        if (index < args.length) {
            return args[index];
        } else {
            printUsage();
            return null; // Never will happen because printUsage will call exit(1)
        }
    }

    public static void main(String[] args) throws IOException {
        List<File> ndkSos = new ArrayList<File>();
        int numNdkSos = 0;
        String ndkLibPath = "";
        String outputFilePath = "./ndk-api.xml";

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                if ("-o".equals(args[i])) {
                    outputFilePath = getExpectedArg(args, ++i);
                } else if ("-n".equals(args[i])) {
                    ndkLibPath = getExpectedArg(args, ++i);
                    File file = new File(ndkLibPath);
                    if (file.isDirectory()) {
                        ndkSos.addAll(Arrays.asList(file.listFiles(SUPPORTED_FILE_NAME_FILTER)));
                    } else {
                        printUsage();
                    }
                } else {
                    printUsage();
                }
            } else {
                printUsage();
            }
        }

        Document dom;
        // instance of a DocumentBuilderFactory
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            // use factory to get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            // create instance of DOM
            dom = db.newDocument();

            // create the root element
            Element apiEle = dom.createElement(API_TAG);
            Element pkgEle = dom.createElement(PACKAGE_TAG);
            setAttribute(dom, pkgEle, ATTRIBUTE_NAME, NDK_PACKAGE_NAME);
            apiEle.appendChild(pkgEle);
            dom.appendChild(apiEle);

            for (File ndkSo : ndkSos) {
                ReadElf re = ReadElf.read(ndkSo);
                re.getDynamicSymbol("");
                ReadElf.Symbol[] symArr = re.getDynSymArr();
                System.out.println(ndkSo.getName());
                Element classEle = addToDom(dom, pkgEle, symArr, ndkSo.getName().toLowerCase());
                pkgEle.appendChild(classEle);
            }

            try {
                Transformer tr = TransformerFactory.newInstance().newTransformer();
                // enable indent in result file
                tr.setOutputProperty(OutputKeys.INDENT, "yes");
                tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                // send DOM to file
                tr.transform(
                        new DOMSource(dom), new StreamResult(new FileOutputStream(outputFilePath)));

            } catch (TransformerException te) {
                System.out.println(te.getMessage());
            } catch (IOException ioe) {
                System.out.println(ioe.getMessage());
            }
        } catch (ParserConfigurationException pce) {
            System.out.println("UsersXML: Error trying to instantiate DocumentBuilder " + pce);
        }
    }

    public static Element addToDom(
            Document dom, Element pkgEle, ReadElf.Symbol[] symArr, String libName) {
        Element classEle = createClassEle(dom, libName);
        for (int i = 0; i < symArr.length; i++) {
            if (symArr[i].isExtern()) {
                Element methodEle;
                if(isInternalSymbol(symArr[i])) {
                    continue;
                }

                if (symArr[i].type == ReadElf.Symbol.STT_OBJECT) {
                    methodEle = createFieldEle(dom, symArr[i].name);
                } else {
                    methodEle = createMethodEle(dom, symArr[i].name);
                }

                System.out.println(symArr[i].name);
                classEle.appendChild(methodEle);
            }
        }
        return classEle;
    }

    public static void addToDom(Document dom, Element pkgEle, ReadElf.Symbol[] symArr) {
        HashMap<String, Element> classEleMap = new HashMap<String, Element>();
        for (int i = 0; i < symArr.length; i++) {
            if (symArr[i].isExtern()) {
                Element methodEle;
                if (symArr[i].type == ReadElf.Symbol.STT_OBJECT) {
                    methodEle = createFieldEle(dom, symArr[i].name);
                } else {
                    methodEle = createMethodEle(dom, symArr[i].name);
                }

                System.out.println(symArr[i].name);

                // add to the class element
                String libName = symArr[i].getVerDefLibName();
                Element classEle = classEleMap.get(libName);
                if (classEle == null) {
                    classEle = createClassEle(dom, libName);
                    classEleMap.put(libName, classEle);
                }
                classEle.appendChild(methodEle);
            }
        }
        Iterator ite = classEleMap.entrySet().iterator();
        while (ite.hasNext()) {
            Map.Entry<String, Element> entry = (Map.Entry<String, Element>) ite.next();
            pkgEle.appendChild(entry.getValue());
        }
    }

    public static void saveToXML(String xml, ReadElf.Symbol[] symArr) {
        Document dom;
        Element ele = null;

        // instance of a DocumentBuilderFactory
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            // use factory to get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            // create instance of DOM
            dom = db.newDocument();

            // create the root element
            Element apiEle = dom.createElement(API_TAG);
            Element packageEle = dom.createElement(PACKAGE_TAG);
            setAttribute(dom, packageEle, ATTRIBUTE_NAME, NDK_PACKAGE_NAME);
            Element classEle = createClassEle(dom, "class");
            packageEle.appendChild(classEle);
            apiEle.appendChild(packageEle);
            dom.appendChild(apiEle);

            for (int i = 0; i < symArr.length; i++) {
                if (symArr[i].isExtern()) {
                    Element methodEle;
                    if (symArr[i].type == ReadElf.Symbol.STT_OBJECT) {
                        methodEle = createFieldEle(dom, symArr[i].name);
                    } else {
                        methodEle = createMethodEle(dom, symArr[i].name);
                    }
                    classEle.appendChild(methodEle);
                }
            }

            try {
                Transformer tr = TransformerFactory.newInstance().newTransformer();
                // enable indent in result file
                tr.setOutputProperty(OutputKeys.INDENT, "yes");
                tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                // send DOM to file
                tr.transform(new DOMSource(dom), new StreamResult(new FileOutputStream(xml)));

            } catch (TransformerException te) {
                System.out.println(te.getMessage());
            } catch (IOException ioe) {
                System.out.println(ioe.getMessage());
            }
        } catch (ParserConfigurationException pce) {
            System.out.println("UsersXML: Error trying to instantiate DocumentBuilder " + pce);
        }
    }

    protected static boolean isInternalSymbol(ReadElf.Symbol sym) {
        String value = sInternalSymMap.get(sym.name);
        if (value == null) {
            return false;
        } else {
            return true;
        }
    }

    protected static void setAttribute(Document doc, Node elem, String name, String value) {
        Attr attr = doc.createAttribute(name);
        attr.setNodeValue(value);
        elem.getAttributes().setNamedItem(attr);
    }

    protected static Element createClassEle(Document doc, String name) {
        Element ele = doc.createElement(CLASS_TAG);
        setAttribute(doc, ele, ATTRIBUTE_NAME, name);
        setAttribute(doc, ele, "abstract", "false");
        setAttribute(doc, ele, "static", "false");
        setAttribute(doc, ele, "final", "true");
        setAttribute(doc, ele, "deprecated", "not deprecated");
        setAttribute(doc, ele, "visibility", "public");
        return ele;
    }

    protected static Element createMethodEle(Document doc, String name) {
        Element ele = doc.createElement(METHOD_TAG);
        setAttribute(doc, ele, ATTRIBUTE_NAME, name);
        setAttribute(doc, ele, "return", NDK_DUMMY_RETURN_TYPE);
        setAttribute(doc, ele, "abstract", "false");
        setAttribute(doc, ele, "native", "true");
        setAttribute(doc, ele, "synchronized", "true");
        setAttribute(doc, ele, "static", "false");
        setAttribute(doc, ele, "final", "true");
        setAttribute(doc, ele, "deprecated", "not deprecated");
        setAttribute(doc, ele, "visibility", "public");
        return ele;
    }

    protected static Element createFieldEle(Document doc, String name) {
        Element ele = doc.createElement(FIELD_TAG);
        setAttribute(doc, ele, ATTRIBUTE_NAME, name);
        setAttribute(doc, ele, "type", "native");
        setAttribute(doc, ele, "transient", "false");
        setAttribute(doc, ele, "volatile", "false");
        setAttribute(doc, ele, "value", "");
        setAttribute(doc, ele, "static", "false");
        setAttribute(doc, ele, "final", "true");
        setAttribute(doc, ele, "deprecated", "not deprecated");
        setAttribute(doc, ele, "visibility", "public");
        return ele;
    }
}
