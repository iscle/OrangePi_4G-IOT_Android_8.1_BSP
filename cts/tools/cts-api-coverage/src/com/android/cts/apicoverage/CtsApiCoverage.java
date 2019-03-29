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

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ReadElf;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.Annotation;
import org.jf.dexlib2.iface.AnnotationElement;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.value.StringEncodedValue;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.transform.TransformerException;

/**
 * Tool that generates a report of what Android framework methods are being called from a given
 * set of APKS. See the {@link #printUsage()} method for more details.
 */
public class CtsApiCoverage {

    private static final FilenameFilter SUPPORTED_FILE_NAME_FILTER = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            String fileName = name.toLowerCase();
            return fileName.endsWith(".apk") || fileName.endsWith(".jar");
        }
    };

    private static final int FORMAT_TXT = 0;

    private static final int FORMAT_XML = 1;

    private static final int FORMAT_HTML = 2;

    private static final String CDD_REQUIREMENT_ANNOTATION = "Lcom/android/compatibility/common/util/CddTest;";

    private static final String CDD_REQUIREMENT_ELEMENT_NAME = "requirement";

    private static final String NDK_PACKAGE_NAME = "ndk";

    private static final String NDK_DUMMY_RETURN_TYPE = "na";

    private static void printUsage() {
        System.out.println("Usage: cts-api-coverage [OPTION]... [APK]...");
        System.out.println();
        System.out.println("Generates a report about what Android framework methods are called ");
        System.out.println("from the given APKs.");
        System.out.println();
        System.out.println("Use the Makefiles rules in CtsCoverage.mk to generate the report ");
        System.out.println("rather than executing this directly. If you still want to run this ");
        System.out.println("directly, then this must be used from the $ANDROID_BUILD_TOP ");
        System.out.println("directory and dexdeps must be built via \"make dexdeps\".");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -o FILE                output file or standard out if not given");
        System.out.println("  -f [txt|xml|html]      format of output");
        System.out.println("  -d PATH                path to dexdeps or expected to be in $PATH");
        System.out.println("  -a PATH                path to the API XML file");
        System.out.println(
                "  -n PATH                path to the NDK API XML file, which can be updated via ndk-api-report with the ndk target");
        System.out.println("  -p PACKAGENAMEPREFIX   report coverage only for package that start with");
        System.out.println("  -t TITLE               report title");
        System.out.println("  -a API                 the Android API Level");
        System.out.println("  -b BITS                64 or 32 bits, default 64");
        System.out.println();
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        List<File> testApks = new ArrayList<File>();
        File outputFile = null;
        int format = FORMAT_TXT;
        String dexDeps = "dexDeps";
        String apiXmlPath = "";
        String napiXmlPath = "";
        PackageFilter packageFilter = new PackageFilter();
        String reportTitle = "CTS API Coverage";
        int apiLevel = Integer.MAX_VALUE;
        String testCasesFolder = "";
        String bits = "64";

        List<File> notFoundTestApks = new ArrayList<File>();
        int numTestApkArgs = 0;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("-")) {
                if ("-o".equals(args[i])) {
                    outputFile = new File(getExpectedArg(args, ++i));
                } else if ("-f".equals(args[i])) {
                    String formatSpec = getExpectedArg(args, ++i);
                    if ("xml".equalsIgnoreCase(formatSpec)) {
                        format = FORMAT_XML;
                    } else if ("txt".equalsIgnoreCase(formatSpec)) {
                        format = FORMAT_TXT;
                    } else if ("html".equalsIgnoreCase(formatSpec)) {
                        format = FORMAT_HTML;
                    } else {
                        printUsage();
                    }
                } else if ("-d".equals(args[i])) {
                    dexDeps = getExpectedArg(args, ++i);
                } else if ("-a".equals(args[i])) {
                    apiXmlPath = getExpectedArg(args, ++i);
                } else if ("-n".equals(args[i])) {
                    napiXmlPath = getExpectedArg(args, ++i);
                } else if ("-p".equals(args[i])) {
                    packageFilter.addPrefixToFilter(getExpectedArg(args, ++i));
                } else if ("-t".equals(args[i])) {
                    reportTitle = getExpectedArg(args, ++i);
                } else if ("-a".equals(args[i])) {
                    apiLevel = Integer.parseInt(getExpectedArg(args, ++i));
                } else if ("-b".equals(args[i])) {
                    bits = getExpectedArg(args, ++i);
                } else {
                    printUsage();
                }
            } else {
                File file = new File(args[i]);
                numTestApkArgs++;
                if (file.isDirectory()) {
                    testApks.addAll(Arrays.asList(file.listFiles(SUPPORTED_FILE_NAME_FILTER)));
                    testCasesFolder = args[i];
                } else if (file.isFile()) {
                    testApks.add(file);
                } else {
                    notFoundTestApks.add(file);
                }
            }
        }

        if (!notFoundTestApks.isEmpty()) {
            String msg = String.format(Locale.US, "%d/%d testApks not found: %s",
                    notFoundTestApks.size(), numTestApkArgs, notFoundTestApks);
            throw new IllegalArgumentException(msg);
        }

        /*
         * 1. Create an ApiCoverage object that is a tree of Java objects representing the API
         *    in current.xml. The object will have no information about the coverage for each
         *    constructor or method yet.
         *
         * 2. For each provided APK, scan it using dexdeps, parse the output of dexdeps, and
         *    call methods on the ApiCoverage object to cumulatively add coverage stats.
         *
         * 3. Output a report based on the coverage stats in the ApiCoverage object.
         */

        ApiCoverage apiCoverage = getEmptyApiCoverage(apiXmlPath);
        CddCoverage cddCoverage = getEmptyCddCoverage();

        if (!napiXmlPath.equals("")) {
            System.out.println("napiXmlPath: " + napiXmlPath);
            ApiCoverage napiCoverage = getEmptyApiCoverage(napiXmlPath);
            ApiPackage napiPackage = napiCoverage.getPackage(NDK_PACKAGE_NAME);
            System.out.println(
                    String.format(
                            "%s, NDK Methods = %d, MemberSize = %d",
                            napiXmlPath,
                            napiPackage.getTotalMethods(),
                            napiPackage.getMemberSize()));
            apiCoverage.addPackage(napiPackage);
        }

        // Add superclass information into api coverage.
        apiCoverage.resolveSuperClasses();
        for (File testApk : testApks) {
            addApiCoverage(apiCoverage, testApk, dexDeps);
            addCddCoverage(cddCoverage, testApk, apiLevel);
        }

        try {
            // Add coverage for GTest modules
            addGTestNdkApiCoverage(apiCoverage, testCasesFolder, bits);
        } catch (Exception e) {
            System.out.println("warning: addGTestNdkApiCoverage failed to add to apiCoverage:");
            e.printStackTrace();
        }

        try {
            // Add coverage for APK with Share Objects
            addNdkApiCoverage(apiCoverage, testCasesFolder, bits);
        } catch (Exception e) {
            System.out.println("warning: addNdkApiCoverage failed to add to apiCoverage:");
            e.printStackTrace();
        }

        outputCoverageReport(apiCoverage, cddCoverage, testApks, outputFile,
            format, packageFilter, reportTitle);
    }

    /** Get the argument or print out the usage and exit. */
    private static String getExpectedArg(String[] args, int index) {
        if (index < args.length) {
            return args[index];
        } else {
            printUsage();
            return null;    // Never will happen because printUsage will call exit(1)
        }
    }

    /**
     * Creates an object representing the API that will be used later to collect coverage
     * statistics as we iterate over the test APKs.
     *
     * @param apiXmlPath to the API XML file
     * @return an {@link ApiCoverage} object representing the API in current.xml without any
     *     coverage statistics yet
     */
    private static ApiCoverage getEmptyApiCoverage(String apiXmlPath)
            throws SAXException, IOException {
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        CurrentXmlHandler currentXmlHandler = new CurrentXmlHandler();
        xmlReader.setContentHandler(currentXmlHandler);

        File currentXml = new File(apiXmlPath);
        FileReader fileReader = null;
        try {
            fileReader = new FileReader(currentXml);
            xmlReader.parse(new InputSource(fileReader));
        } finally {
            if (fileReader != null) {
                fileReader.close();
            }
        }

        return currentXmlHandler.getApi();
    }

    /**
     * Adds coverage information gleamed from running dexdeps on the APK to the
     * {@link ApiCoverage} object.
     *
     * @param apiCoverage object to which the coverage statistics will be added to
     * @param testApk containing the tests that will be scanned by dexdeps
     */
    private static void addApiCoverage(ApiCoverage apiCoverage, File testApk, String dexdeps)
            throws SAXException, IOException {
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        String testApkName = testApk.getName();
        DexDepsXmlHandler dexDepsXmlHandler = new DexDepsXmlHandler(apiCoverage, testApkName);
        xmlReader.setContentHandler(dexDepsXmlHandler);

        String apkPath = testApk.getPath();
        Process process = new ProcessBuilder(dexdeps, "--format=xml", apkPath).start();
        try {
            xmlReader.parse(new InputSource(process.getInputStream()));
        } catch (SAXException e) {
          // Catch this exception, but continue. SAXException is acceptable in cases
          // where the apk does not contain a classes.dex and therefore parsing won't work.
          System.err.println("warning: dexdeps failed for: " + apkPath);
        }
    }

    /**
     * Adds coverage information from native code symbol array to the {@link ApiCoverage} object.
     *
     * @param apiPackage object to which the coverage statistics will be added to
     * @param symArr containing native code symbols
     * @param testModules containing a list of TestModule
     * @param moduleName test module name
     */
    private static void addNdkSymArrToApiCoverage(
            ApiCoverage apiCoverage, List<TestModule> testModules)
            throws SAXException, IOException {

        final List<String> parameterTypes = new ArrayList<String>();
        final ApiPackage apiPackage = apiCoverage.getPackage(NDK_PACKAGE_NAME);

        if (apiPackage != null) {
            for (TestModule tm : testModules) {
                final String moduleName = tm.getModuleName();
                final ReadElf.Symbol[] symArr = tm.getDynSymArr();
                if (symArr != null) {
                    for (ReadElf.Symbol sym : symArr) {
                        if (sym.isGlobalUnd()) {
                            String className = sym.getExternalLibFileName();
                            ApiClass apiClass = apiPackage.getClass(className);
                            if (apiClass != null) {
                                apiClass.markMethodCovered(
                                        sym.name,
                                        parameterTypes,
                                        NDK_DUMMY_RETURN_TYPE,
                                        moduleName);
                            } else {
                                System.err.println(
                                        String.format(
                                                "warning: addNdkApiCoverage failed to getClass: %s",
                                                className));
                            }
                        }
                    }
                } else {
                    System.err.println(
                            String.format(
                                    "warning: addNdkSymbolArrToApiCoverage failed to getSymArr: %s",
                                    moduleName));
                }
            }
        } else {
            System.err.println(
                    String.format(
                            "warning: addNdkApiCoverage failed to getPackage: %s",
                            NDK_PACKAGE_NAME));
        }
    }

    /**
     * Adds coverage information gleamed from readelf on so in the APK to the {@link ApiCoverage}
     * object.
     *
     * @param apiCoverage object to which the coverage statistics will be added to
     * @param testCasesFolder containing GTest modules
     * @param bits 64 or 32 bits of executiable
     */
    private static void addNdkApiCoverage(
            ApiCoverage apiCoverage, String testCasesFolder, String bits)
            throws SAXException, IOException {
        ApkNdkApiReport apiReport = ApkNdkApiReport.parseTestcasesFolder(testCasesFolder, bits);
        if (apiReport != null) {
            addNdkSymArrToApiCoverage(apiCoverage, apiReport.getTestModules());
        } else {
            System.err.println(
                    String.format(
                            "warning: addNdkApiCoverage failed to get GTestApiReport from: %s @ %s bits",
                            testCasesFolder, bits));
        }
    }

    /**
     * Adds GTest coverage information gleamed from running ReadElf on the executiable to the {@link
     * ApiCoverage} object.
     *
     * @param apiCoverage object to which the coverage statistics will be added to
     * @param testCasesFolder containing GTest modules
     * @param bits 64 or 32 bits of executiable
     */
    private static void addGTestNdkApiCoverage(
            ApiCoverage apiCoverage, String testCasesFolder, String bits)
            throws SAXException, IOException {
        GTestApiReport apiReport = GTestApiReport.parseTestcasesFolder(testCasesFolder, bits);
        if (apiReport != null) {
            addNdkSymArrToApiCoverage(apiCoverage, apiReport.getTestModules());
        } else {
            System.err.println(
                    String.format(
                            "warning: addGTestNdkApiCoverage failed to get GTestApiReport from: %s @ %s bits",
                            testCasesFolder, bits));
        }
    }

    private static void addCddCoverage(CddCoverage cddCoverage, File testSource, int api)
            throws IOException {

        if (testSource.getName().endsWith(".apk")) {
            addCddApkCoverage(cddCoverage, testSource, api);
        } else if (testSource.getName().endsWith(".jar")) {
            addCddJarCoverage(cddCoverage, testSource);
        } else {
            System.err.println("Unsupported file type for CDD coverage: " + testSource.getPath());
        }
    }

    private static void addCddJarCoverage(CddCoverage cddCoverage, File testSource)
            throws IOException {

        Collection<Class<?>> classes = JarTestFinder.getClasses(testSource);
        for (Class<?> c : classes) {
            for (java.lang.reflect.Method m : c.getMethods()) {
                if (m.isAnnotationPresent(CddTest.class)) {
                    CddTest cddTest = m.getAnnotation(CddTest.class);
                    CddCoverage.TestMethod testMethod =
                            new CddCoverage.TestMethod(
                                    testSource.getName(), c.getName(), m.getName());
                    cddCoverage.addCoverage(cddTest.requirement(), testMethod);
                }
            }
        }
    }

    private static void addCddApkCoverage(
        CddCoverage cddCoverage, File testSource, int api)
            throws IOException {

        DexFile dexFile = null;
        try {
            dexFile = DexFileFactory.loadDexFile(testSource, Opcodes.forApi(api));
        } catch (IOException | DexFileFactory.DexFileNotFoundException e) {
            System.err.println("Unable to load dex file: " + testSource.getPath());
            return;
        }

        String moduleName = testSource.getName();
        for (ClassDef classDef : dexFile.getClasses()) {
            String className = classDef.getType();
            handleAnnotations(
                cddCoverage, moduleName, className, null /*methodName*/,
                classDef.getAnnotations());

            for (Method method : classDef.getMethods()) {
                String methodName = method.getName();
                handleAnnotations(
                    cddCoverage, moduleName, className, methodName, method.getAnnotations());
            }
        }
    }

    private static void handleAnnotations(
            CddCoverage cddCoverage, String moduleName, String className,
                    String methodName, Set<? extends Annotation> annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.getType().equals(CDD_REQUIREMENT_ANNOTATION)) {
                for (AnnotationElement annotationElement : annotation.getElements()) {
                    if (annotationElement.getName().equals(CDD_REQUIREMENT_ELEMENT_NAME)) {
                        String cddRequirement =
                                ((StringEncodedValue) annotationElement.getValue()).getValue();
                        CddCoverage.TestMethod testMethod =
                                new CddCoverage.TestMethod(
                                        moduleName, dexToJavaName(className), methodName);
                        cddCoverage.addCoverage(cddRequirement, testMethod);
                    }
                }
            }
        }
    }

    /**
     * Given a string like Landroid/app/cts/DownloadManagerTest;
     * return android.app.cts.DownloadManagerTest.
     */
    private static String dexToJavaName(String dexName) {
        if (!dexName.startsWith("L") || !dexName.endsWith(";")) {
            return dexName;
        }
        dexName = dexName.replace('/', '.');
        if (dexName.length() > 2) {
            dexName = dexName.substring(1, dexName.length() - 1);
        }
        return dexName;
    }

    private static CddCoverage getEmptyCddCoverage() {
        CddCoverage cddCoverage = new CddCoverage();
        // TODO(nicksauer): Read in the valid list of requirements
        return cddCoverage;
    }

    private static void outputCoverageReport(ApiCoverage apiCoverage, CddCoverage cddCoverage,
            List<File> testApks, File outputFile, int format, PackageFilter packageFilter,
            String reportTitle)
                throws IOException, TransformerException, InterruptedException {

        OutputStream out = outputFile != null
                ? new FileOutputStream(outputFile)
                : System.out;

        try {
            switch (format) {
                case FORMAT_TXT:
                    TextReport.printTextReport(apiCoverage, cddCoverage, packageFilter, out);
                    break;

                case FORMAT_XML:
                    XmlReport.printXmlReport(testApks, apiCoverage, cddCoverage,
                        packageFilter, reportTitle, out);
                    break;

                case FORMAT_HTML:
                    HtmlReport.printHtmlReport(testApks, apiCoverage, cddCoverage,
                        packageFilter, reportTitle, out);
                    break;
            }
        } finally {
            out.close();
        }
    }
}
