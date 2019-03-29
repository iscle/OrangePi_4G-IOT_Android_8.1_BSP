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


import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
/**
 * Class that outputs an XML report of the {@link ApiCoverage} collected. It can be viewed in a
 * browser when used with the api-coverage.css and api-coverage.xsl files.
 */
class ApkNdkApiReport {
    public static final String FILE_FILTER_EXT = ".apk";
    public static final String DEFAULT_OUTPUT_FILE_NAME = "./apk-ndk-coverage.txt";

    private static final FilenameFilter SUPPORTED_FILE_NAME_FILTER =
            new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    String fileName = name.toLowerCase();
                    return fileName.endsWith(FILE_FILTER_EXT);
                }
            };

    private static void printUsage() {
        System.out.println("Usage: ApkNdkApiReport [OPTION]... [APK]...");
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
        ApkNdkApiReport apiReport;
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

    ApkNdkApiReport(List<TestModule> testModules, String bits) {
        mTestModules = testModules;
        mBits = bits;
    }

    public List<TestModule> getTestModules() {
        return mTestModules;
    }

    public String getBits() {
        return mBits;
    }

    public static ApkNdkApiReport parseTestcasesFolder(String testCasePath, String bits)
            throws IOException, SAXException {
        File[] testConfigFiles;
        List<TestModule> testModules = new ArrayList<TestModule>();

        File file = new File(testCasePath);
        if (file.isDirectory()) {
            File[] targetFiles = file.listFiles(SUPPORTED_FILE_NAME_FILTER);

            Map<String, String> env = new HashMap<>();
            for (File targetFile : targetFiles) {
                final ZipFile apkFile = new ZipFile(targetFile);
                System.out.println(targetFile.getName());
                try {
                    final Enumeration<? extends ZipEntry> entries = apkFile.entries();
                    while (entries.hasMoreElements()) {
                        final ZipEntry entry = entries.nextElement();

                        if (!entry.getName().matches("lib(.*)" + bits + "(.*)so")) {
                            continue;
                        }

                        System.out.println(entry.getName());

                        //use entry input stream:
                        InputStream is = apkFile.getInputStream(entry);

                        File tempFile = File.createTempFile("ApkNdkApiReport", ".so");
                        tempFile.deleteOnExit();
                        FileOutputStream fos = new FileOutputStream(tempFile);

                        byte[] bytes = new byte[4096];
                        int length;
                        while ((length = is.read(bytes)) >= 0) {
                            fos.write(bytes, 0, length);
                        }
                        is.close();
                        fos.close();

                        testModules.add(new TestModule(tempFile, targetFile.getName(), "jUnit"));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            return null;
        }
        return new ApkNdkApiReport(testModules, bits);
    }
}
