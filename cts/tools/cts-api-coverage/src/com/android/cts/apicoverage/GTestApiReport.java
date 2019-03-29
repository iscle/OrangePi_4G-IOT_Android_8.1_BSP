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


import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
/**
 * Class that outputs an XML report of the {@link ApiCoverage} collected. It can be viewed in a
 * browser when used with the api-coverage.css and api-coverage.xsl files.
 */
class GTestApiReport {
    public static final String CONFIG_EXT = ".config";
    public static final String DEFAULT_OUTPUT_FILE_NAME = "./gtest-coverage.txt";
    public static final String TEST_TYPE = "com.android.tradefed.testtype.GTest";

    private static final FilenameFilter SUPPORTED_FILE_NAME_FILTER =
            new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    String fileName = name.toLowerCase();
                    return fileName.endsWith(CONFIG_EXT);
                }
            };

    private static void printUsage() {
        System.out.println("Usage: GTestApiXmlReport [OPTION]... [APK]...");
        System.out.println();
        System.out.println("Generates a report about what Android NDK methods.");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -o FILE                output file or standard out if not given");
        System.out.println("  -t PATH                path to the CTS testcases Folder");
        System.out.println("  -b BITS                64 or 32");
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

    public static void main(String[] args) throws IOException, SAXException {
        GTestApiReport apiReport;
        String testCasePath = "";
        String bits = "64";
        String outputFileName = DEFAULT_OUTPUT_FILE_NAME;
        int numTestModule = 0;

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                if ("-o".equals(args[i])) {
                    outputFileName = getExpectedArg(args, ++i);
                } else if ("-t".equals(args[i])) {
                    testCasePath = getExpectedArg(args, ++i);
                } else if ("-b".equals(args[i])) {
                    bits = getExpectedArg(args, ++i);
                } else {
                    printUsage();
                }
            } else {
                printUsage();
            }
        }

        apiReport = parseTestcasesFolder(testCasePath, bits);
        if (apiReport != null) {
            for (TestModule tm : apiReport.mTestModules) {
                tm.getDynSymArr();
            }
        } else {
            printUsage();
        }
    }

    private List<TestModule> mTestModules;
    private String mBits;

    GTestApiReport(List<TestModule> testModules, String bits) {
        mTestModules = testModules;
        mBits = bits;
    }

    public List<TestModule> getTestModules() {
        return mTestModules;
    }

    public String getBits() {
        return mBits;
    }

    public static GTestApiReport parseTestcasesFolder(String testCasePath, String bits)
            throws IOException, SAXException {
        File[] testConfigFiles;
        List<TestModule> testModules = new ArrayList<TestModule>();

        File file = new File(testCasePath);
        if (file.isDirectory()) {
            testConfigFiles = file.listFiles(SUPPORTED_FILE_NAME_FILTER);

            for (File testConfigFile : testConfigFiles) {
                XMLReader xmlReader = XMLReaderFactory.createXMLReader();
                TestModuleConfigHandler testModuleXmlHandler = new TestModuleConfigHandler();
                xmlReader.setContentHandler(testModuleXmlHandler);
                FileReader fileReader = null;

                try {
                    fileReader = new FileReader(testConfigFile);
                    xmlReader.parse(new InputSource(fileReader));
                    if (TEST_TYPE.equalsIgnoreCase(testModuleXmlHandler.getTestClassName())) {
                        File gTestExe =
                                new File(
                                        testCasePath
                                                + "/"
                                                + testModuleXmlHandler.getModuleName()
                                                + bits);

                        System.out.println(gTestExe.getName());
                        System.out.println(
                                String.format(
                                        "%s: %s, %s",
                                        testConfigFile.getName(),
                                        testModuleXmlHandler.getModuleName(),
                                        testModuleXmlHandler.getTestClassName()));

                        testModules.add(
                                new TestModule(
                                        gTestExe,
                                        testModuleXmlHandler.getModuleName(),
                                        testModuleXmlHandler.getTestClassName()));
                    }

                } finally {
                    if (fileReader != null) {
                        fileReader.close();
                    }
                }
            }
        } else {
            return null;
        }
        return new GTestApiReport(testModules, bits);
    }
}
