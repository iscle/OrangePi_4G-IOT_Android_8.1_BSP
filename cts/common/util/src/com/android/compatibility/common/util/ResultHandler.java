/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.util;

import com.android.compatibility.common.util.ChecksumReporter.ChecksumValidationException;

import com.google.common.base.Strings;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
/**
 * Handles conversion of results to/from files.
 */
public class ResultHandler {

    private static final String ENCODING = "UTF-8";
    private static final String TYPE = "org.kxml2.io.KXmlParser,org.kxml2.io.KXmlSerializer";
    private static final String NS = null;
    private static final String RESULT_FILE_VERSION = "5.0";
    public static final String TEST_RESULT_FILE_NAME = "test_result.xml";
    public static final String FAILURE_REPORT_NAME = "test_result_failures.html";
    private static final String FAILURE_XSL_FILE_NAME = "compatibility_failures.xsl";

    public static final String[] RESULT_RESOURCES = {
        "compatibility_result.css",
        "compatibility_result.xsd",
        "compatibility_result.xsl",
        "logo.png"
    };

    // XML constants
    private static final String ABI_ATTR = "abi";
    private static final String BUGREPORT_TAG = "BugReport";
    private static final String BUILD_FINGERPRINT = "build_fingerprint";
    private static final String BUILD_ID = "build_id";
    private static final String BUILD_PRODUCT = "build_product";
    private static final String BUILD_TAG = "Build";
    private static final String CASE_TAG = "TestCase";
    private static final String COMMAND_LINE_ARGS = "command_line_args";
    private static final String DEVICES_ATTR = "devices";
    private static final String DONE_ATTR = "done";
    private static final String END_DISPLAY_TIME_ATTR = "end_display";
    private static final String END_TIME_ATTR = "end";
    private static final String FAILED_ATTR = "failed";
    private static final String FAILURE_TAG = "Failure";
    private static final String HOST_NAME_ATTR = "host_name";
    private static final String JAVA_VENDOR_ATTR = "java_vendor";
    private static final String JAVA_VERSION_ATTR = "java_version";
    private static final String LOGCAT_TAG = "Logcat";
    private static final String LOG_URL_ATTR = "log_url";
    private static final String MESSAGE_ATTR = "message";
    private static final String MODULE_TAG = "Module";
    private static final String MODULES_DONE_ATTR = "modules_done";
    private static final String MODULES_TOTAL_ATTR = "modules_total";
    private static final String NAME_ATTR = "name";
    private static final String OS_ARCH_ATTR = "os_arch";
    private static final String OS_NAME_ATTR = "os_name";
    private static final String OS_VERSION_ATTR = "os_version";
    private static final String PASS_ATTR = "pass";
    private static final String REPORT_VERSION_ATTR = "report_version";
    private static final String REFERENCE_URL_ATTR = "reference_url";
    private static final String RESULT_ATTR = "result";
    private static final String RESULT_TAG = "Result";
    private static final String RUNTIME_ATTR = "runtime";
    private static final String SCREENSHOT_TAG = "Screenshot";
    private static final String SKIPPED_ATTR = "skipped";
    private static final String STACK_TAG = "StackTrace";
    private static final String START_DISPLAY_TIME_ATTR = "start_display";
    private static final String START_TIME_ATTR = "start";
    private static final String SUITE_NAME_ATTR = "suite_name";
    private static final String SUITE_PLAN_ATTR = "suite_plan";
    private static final String SUITE_VERSION_ATTR = "suite_version";
    private static final String SUITE_BUILD_ATTR = "suite_build_number";
    private static final String SUMMARY_TAG = "Summary";
    private static final String TEST_TAG = "Test";


    /**
     * Returns IInvocationResults that can be queried for general reporting information, but that
     * do not store underlying module data. Useful for summarizing invocation history.
     * @param resultsDir
     * @param useChecksum
     */
    public static List<IInvocationResult> getLightResults(File resultsDir) {
        List<IInvocationResult> results = new ArrayList<>();
        List<File> files = getResultDirectories(resultsDir);
        for (File resultDir : files) {
            IInvocationResult result = getResultFromDir(resultDir, false);
            if (result != null) {
                results.add(new LightInvocationResult(result));
                result = null; // ensure all references are removed to free memory
            }
        }
        // Sort the table entries on each entry's timestamp.
        Collections.sort(results,  (result1, result2) -> Long.compare(
                result1.getStartTime(),
                result2.getStartTime()));
        return results;
    }

    /**
     * @param resultDir
     * @return an IInvocationResult for this result, or null upon error
     */
    public static IInvocationResult getResultFromDir(File resultDir) {
        return getResultFromDir(resultDir, false);
    }

    /**
     * @param resultDir
     * @param useChecksum
     * @return an IInvocationResult for this result, or null upon error
     */
    public static IInvocationResult getResultFromDir(File resultDir, Boolean useChecksum) {
        try {
            File resultFile = new File(resultDir, TEST_RESULT_FILE_NAME);
            if (!resultFile.exists()) {
                return null;
            }
            Boolean invocationUseChecksum = useChecksum;
            IInvocationResult invocation = new InvocationResult();
            invocation.setRetryDirectory(resultDir);
            ChecksumReporter checksumReporter = null;
            if (invocationUseChecksum) {
                try {
                    checksumReporter = ChecksumReporter.load(resultDir);
                    invocation.setRetryChecksumStatus(RetryChecksumStatus.RetryWithChecksum);
                } catch (ChecksumValidationException e) {
                    // Unable to read checksum form previous execution
                    invocation.setRetryChecksumStatus(RetryChecksumStatus.RetryWithoutChecksum);
                    invocationUseChecksum = false;
                }
            }
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new FileReader(resultFile));

            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, NS, RESULT_TAG);
            invocation.setStartTime(Long.valueOf(
                    parser.getAttributeValue(NS, START_TIME_ATTR)));
            invocation.setTestPlan(parser.getAttributeValue(NS, SUITE_PLAN_ATTR));
            invocation.setCommandLineArgs(parser.getAttributeValue(NS, COMMAND_LINE_ARGS));
            String deviceList = parser.getAttributeValue(NS, DEVICES_ATTR);
            for (String device : deviceList.split(",")) {
                invocation.addDeviceSerial(device);
            }

            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, NS, BUILD_TAG);
            invocation.addInvocationInfo(BUILD_ID, parser.getAttributeValue(NS, BUILD_ID));
            invocation.addInvocationInfo(BUILD_PRODUCT, parser.getAttributeValue(NS,
                    BUILD_PRODUCT));
            invocation.setBuildFingerprint(parser.getAttributeValue(NS, BUILD_FINGERPRINT));

            // TODO(stuartscott): may want to reload these incase the retry was done with
            // --skip-device-info flag
            parser.nextTag();
            parser.require(XmlPullParser.END_TAG, NS, BUILD_TAG);
            parser.nextTag();
            parser.require(XmlPullParser.START_TAG, NS, SUMMARY_TAG);
            parser.nextTag();
            parser.require(XmlPullParser.END_TAG, NS, SUMMARY_TAG);
            while (parser.nextTag() == XmlPullParser.START_TAG) {
                parser.require(XmlPullParser.START_TAG, NS, MODULE_TAG);
                String name = parser.getAttributeValue(NS, NAME_ATTR);
                String abi = parser.getAttributeValue(NS, ABI_ATTR);
                String moduleId = AbiUtils.createId(abi, name);
                boolean done = Boolean.parseBoolean(parser.getAttributeValue(NS, DONE_ATTR));
                IModuleResult module = invocation.getOrCreateModule(moduleId);
                module.initializeDone(done);
                long runtime = Long.parseLong(parser.getAttributeValue(NS, RUNTIME_ATTR));
                module.addRuntime(runtime);
                while (parser.nextTag() == XmlPullParser.START_TAG) {
                    parser.require(XmlPullParser.START_TAG, NS, CASE_TAG);
                    String caseName = parser.getAttributeValue(NS, NAME_ATTR);
                    ICaseResult testCase = module.getOrCreateResult(caseName);
                    while (parser.nextTag() == XmlPullParser.START_TAG) {
                        parser.require(XmlPullParser.START_TAG, NS, TEST_TAG);
                        String testName = parser.getAttributeValue(NS, NAME_ATTR);
                        ITestResult test = testCase.getOrCreateResult(testName);
                        String result = parser.getAttributeValue(NS, RESULT_ATTR);
                        String skipped = parser.getAttributeValue(NS, SKIPPED_ATTR);
                        if (skipped != null && Boolean.parseBoolean(skipped)) {
                            // mark test passed and skipped
                            test.skipped();
                        } else {
                            // only apply result status directly if test was not skipped
                            test.setResultStatus(TestStatus.getStatus(result));
                        }
                        test.setRetry(true);
                        while (parser.nextTag() == XmlPullParser.START_TAG) {
                            if (parser.getName().equals(FAILURE_TAG)) {
                                test.setMessage(parser.getAttributeValue(NS, MESSAGE_ATTR));
                                if (parser.nextTag() == XmlPullParser.START_TAG) {
                                    parser.require(XmlPullParser.START_TAG, NS, STACK_TAG);
                                    test.setStackTrace(parser.nextText());
                                    parser.require(XmlPullParser.END_TAG, NS, STACK_TAG);
                                    parser.nextTag();
                                }
                                parser.require(XmlPullParser.END_TAG, NS, FAILURE_TAG);
                            } else if (parser.getName().equals(BUGREPORT_TAG)) {
                                test.setBugReport(parser.nextText());
                                parser.require(XmlPullParser.END_TAG, NS, BUGREPORT_TAG);
                            } else if (parser.getName().equals(LOGCAT_TAG)) {
                                test.setLog(parser.nextText());
                                parser.require(XmlPullParser.END_TAG, NS, LOGCAT_TAG);
                            } else if (parser.getName().equals(SCREENSHOT_TAG)) {
                                test.setScreenshot(parser.nextText());
                                parser.require(XmlPullParser.END_TAG, NS, SCREENSHOT_TAG);
                            } else {
                                test.setReportLog(ReportLog.parse(parser));
                            }
                        }
                        parser.require(XmlPullParser.END_TAG, NS, TEST_TAG);
                        Boolean checksumMismatch = invocationUseChecksum
                            && !checksumReporter.containsTestResult(
                                test, module, invocation.getBuildFingerprint());
                        if (checksumMismatch) {
                            test.removeResult();
                        }
                    }
                    parser.require(XmlPullParser.END_TAG, NS, CASE_TAG);
                }
                parser.require(XmlPullParser.END_TAG, NS, MODULE_TAG);
                Boolean checksumMismatch = invocationUseChecksum
                    && !checksumReporter.containsModuleResult(
                            module, invocation.getBuildFingerprint());
                if (checksumMismatch) {
                    module.initializeDone(false);
                }
            }
            parser.require(XmlPullParser.END_TAG, NS, RESULT_TAG);
            return invocation;
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @param result
     * @param resultDir
     * @param startTime
     * @param referenceUrl A nullable string that can contain a URL to a related data
     * @param logUrl A nullable string that can contain a URL to related log files
     * @param commandLineArgs A string containing the arguments to the run command
     * @return The result file created.
     * @throws IOException
     * @throws XmlPullParserException
     */
    public static File writeResults(String suiteName, String suiteVersion, String suitePlan,
            String suiteBuild, IInvocationResult result, File resultDir,
            long startTime, long endTime, String referenceUrl, String logUrl,
            String commandLineArgs)
                    throws IOException, XmlPullParserException {
        int passed = result.countResults(TestStatus.PASS);
        int failed = result.countResults(TestStatus.FAIL);
        File resultFile = new File(resultDir, TEST_RESULT_FILE_NAME);
        OutputStream stream = new FileOutputStream(resultFile);
        XmlSerializer serializer = XmlPullParserFactory.newInstance(TYPE, null).newSerializer();
        serializer.setOutput(stream, ENCODING);
        serializer.startDocument(ENCODING, false);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.processingInstruction(
                "xml-stylesheet type=\"text/xsl\" href=\"compatibility_result.xsl\"");
        serializer.startTag(NS, RESULT_TAG);
        serializer.attribute(NS, START_TIME_ATTR, String.valueOf(startTime));
        serializer.attribute(NS, END_TIME_ATTR, String.valueOf(endTime));
        serializer.attribute(NS, START_DISPLAY_TIME_ATTR, toReadableDateString(startTime));
        serializer.attribute(NS, END_DISPLAY_TIME_ATTR, toReadableDateString(endTime));

        serializer.attribute(NS, SUITE_NAME_ATTR, suiteName);
        serializer.attribute(NS, SUITE_VERSION_ATTR, suiteVersion);
        serializer.attribute(NS, SUITE_PLAN_ATTR, suitePlan);
        serializer.attribute(NS, SUITE_BUILD_ATTR, suiteBuild);
        serializer.attribute(NS, REPORT_VERSION_ATTR, RESULT_FILE_VERSION);
        serializer.attribute(NS, COMMAND_LINE_ARGS, nullToEmpty(commandLineArgs));

        if (referenceUrl != null) {
            serializer.attribute(NS, REFERENCE_URL_ATTR, referenceUrl);
        }

        if (logUrl != null) {
            serializer.attribute(NS, LOG_URL_ATTR, logUrl);
        }

        // Device Info
        Set<String> devices = result.getDeviceSerials();
        StringBuilder deviceList = new StringBuilder();
        boolean first = true;
        for (String device : devices) {
            if (first) {
                first = false;
            } else {
                deviceList.append(",");
            }
            deviceList.append(device);
        }
        serializer.attribute(NS, DEVICES_ATTR, deviceList.toString());

        // Host Info
        String hostName = "";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {}
        serializer.attribute(NS, HOST_NAME_ATTR, hostName);
        serializer.attribute(NS, OS_NAME_ATTR, System.getProperty("os.name"));
        serializer.attribute(NS, OS_VERSION_ATTR, System.getProperty("os.version"));
        serializer.attribute(NS, OS_ARCH_ATTR, System.getProperty("os.arch"));
        serializer.attribute(NS, JAVA_VENDOR_ATTR, System.getProperty("java.vendor"));
        serializer.attribute(NS, JAVA_VERSION_ATTR, System.getProperty("java.version"));

        // Build Info
        serializer.startTag(NS, BUILD_TAG);
        for (Entry<String, String> entry : result.getInvocationInfo().entrySet()) {
            serializer.attribute(NS, entry.getKey(), entry.getValue());
            if (Strings.isNullOrEmpty(result.getBuildFingerprint()) &&
                entry.getKey().equals(BUILD_FINGERPRINT)) {
                result.setBuildFingerprint(entry.getValue());
            }
        }
        serializer.endTag(NS, BUILD_TAG);

        // Summary
        serializer.startTag(NS, SUMMARY_TAG);
        serializer.attribute(NS, PASS_ATTR, Integer.toString(passed));
        serializer.attribute(NS, FAILED_ATTR, Integer.toString(failed));
        serializer.attribute(NS, MODULES_DONE_ATTR,
                Integer.toString(result.getModuleCompleteCount()));
        serializer.attribute(NS, MODULES_TOTAL_ATTR,
                Integer.toString(result.getModules().size()));
        serializer.endTag(NS, SUMMARY_TAG);

        // Results
        for (IModuleResult module : result.getModules()) {
            serializer.startTag(NS, MODULE_TAG);
            serializer.attribute(NS, NAME_ATTR, module.getName());
            serializer.attribute(NS, ABI_ATTR, module.getAbi());
            serializer.attribute(NS, RUNTIME_ATTR, String.valueOf(module.getRuntime()));
            serializer.attribute(NS, DONE_ATTR, Boolean.toString(module.isDone()));
            serializer.attribute(NS, PASS_ATTR,
                    Integer.toString(module.countResults(TestStatus.PASS)));
            for (ICaseResult cr : module.getResults()) {
                serializer.startTag(NS, CASE_TAG);
                serializer.attribute(NS, NAME_ATTR, cr.getName());
                for (ITestResult r : cr.getResults()) {
                    TestStatus status = r.getResultStatus();
                    if (status == null) {
                        continue; // test was not executed, don't report
                    }
                    serializer.startTag(NS, TEST_TAG);
                    serializer.attribute(NS, RESULT_ATTR, status.getValue());
                    serializer.attribute(NS, NAME_ATTR, r.getName());
                    if (r.isSkipped()) {
                        serializer.attribute(NS, SKIPPED_ATTR, Boolean.toString(true));
                    }
                    String message = r.getMessage();
                    if (message != null) {
                        serializer.startTag(NS, FAILURE_TAG);
                        serializer.attribute(NS, MESSAGE_ATTR, message);
                        String stackTrace = r.getStackTrace();
                        if (stackTrace != null) {
                            serializer.startTag(NS, STACK_TAG);
                            serializer.text(stackTrace);
                            serializer.endTag(NS, STACK_TAG);
                        }
                        serializer.endTag(NS, FAILURE_TAG);
                    }
                    String bugreport = r.getBugReport();
                    if (bugreport != null) {
                        serializer.startTag(NS, BUGREPORT_TAG);
                        serializer.text(bugreport);
                        serializer.endTag(NS, BUGREPORT_TAG);
                    }
                    String logcat = r.getLog();
                    if (logcat != null) {
                        serializer.startTag(NS, LOGCAT_TAG);
                        serializer.text(logcat);
                        serializer.endTag(NS, LOGCAT_TAG);
                    }
                    String screenshot = r.getScreenshot();
                    if (screenshot != null) {
                        serializer.startTag(NS, SCREENSHOT_TAG);
                        serializer.text(screenshot);
                        serializer.endTag(NS, SCREENSHOT_TAG);
                    }
                    ReportLog report = r.getReportLog();
                    if (report != null) {
                        ReportLog.serialize(serializer, report);
                    }
                    serializer.endTag(NS, TEST_TAG);
                }
                serializer.endTag(NS, CASE_TAG);
            }
            serializer.endTag(NS, MODULE_TAG);
        }
        serializer.endDocument();
        createChecksum(resultDir, result);
        return resultFile;
    }

    /**
     * Generate html report listing an failed tests
     */
    public static File createFailureReport(File inputXml) {
        File failureReport = new File(inputXml.getParentFile(), FAILURE_REPORT_NAME);
        try (InputStream xslStream = ResultHandler.class.getResourceAsStream(
                String.format("/report/%s", FAILURE_XSL_FILE_NAME));
             OutputStream outputStream = new FileOutputStream(failureReport)) {

            Transformer transformer = TransformerFactory.newInstance().newTransformer(
                    new StreamSource(xslStream));
            transformer.transform(new StreamSource(inputXml), new StreamResult(outputStream));
        } catch (IOException | TransformerException ignored) { }
        return failureReport;
    }

    private static void createChecksum(File resultDir, IInvocationResult invocationResult) {
        RetryChecksumStatus retryStatus = invocationResult.getRetryChecksumStatus();
        switch (retryStatus) {
            case NotRetry: case RetryWithChecksum:
                // Do not disrupt the process if there is a problem generating checksum.
                ChecksumReporter.tryCreateChecksum(resultDir, invocationResult);
                break;
            case RetryWithoutChecksum:
                // If the previous run has an invalid checksum file,
                // copy it into current results folder for future troubleshooting
                File retryDirectory = invocationResult.getRetryDirectory();
                Path retryChecksum = FileSystems.getDefault().getPath(
                        retryDirectory.getAbsolutePath(), ChecksumReporter.NAME);
                if (!retryChecksum.toFile().exists()) {
                    // if no checksum file, check for a copy from a previous retry
                    retryChecksum = FileSystems.getDefault().getPath(
                            retryDirectory.getAbsolutePath(), ChecksumReporter.PREV_NAME);
                }

                if (retryChecksum.toFile().exists()) {
                    File checksumCopy = new File(resultDir, ChecksumReporter.PREV_NAME);
                    try (FileOutputStream stream = new FileOutputStream(checksumCopy)) {
                        Files.copy(retryChecksum, stream);
                    } catch (IOException e) {
                        // Do not disrupt the process if there is a problem copying checksum
                    }
                }
        }
    }


    /**
     * Find the IInvocationResult for the given sessionId.
     */
    public static IInvocationResult findResult(File resultsDir, Integer sessionId)
            throws FileNotFoundException {
        return findResult(resultsDir, sessionId, true);
    }

    /**
     * Find the IInvocationResult for the given sessionId.
     */
    private static IInvocationResult findResult(
            File resultsDir, Integer sessionId, Boolean useChecksum) throws FileNotFoundException {
        if (sessionId < 0) {
            throw new IllegalArgumentException(
                String.format("Invalid session id [%d] ", sessionId));
        }
        File resultDir = getResultDirectory(resultsDir, sessionId);
        IInvocationResult result = getResultFromDir(resultDir, useChecksum);
        if (result == null) {
            throw new RuntimeException(String.format("Could not find session [%d]", sessionId));
        }
        return result;
    }

    /**
     * Get the result directory for the given sessionId.
     */
    public static File getResultDirectory(File resultsDir, Integer sessionId) {
        if (sessionId < 0) {
            throw new IllegalArgumentException(
                String.format("Invalid session id [%d] ", sessionId));
        }
        List<File> allResultDirs = getResultDirectories(resultsDir);
        if (sessionId >= allResultDirs.size()) {
            throw new IllegalArgumentException(String.format("Invalid session id [%d], results" +
                    "directory contains only %d results", sessionId, allResultDirs.size()));
        }
        return allResultDirs.get(sessionId);
    }

    /**
     * Get a list of child directories that contain test invocation results
     * @param resultsDir the root test result directory
     * @return
     */
    public static List<File> getResultDirectories(File resultsDir) {
        List<File> directoryList = new ArrayList<>();
        File[] files = resultsDir.listFiles();
        if (files == null || files.length == 0) {
            // No results, just return the empty list
            return directoryList;
        }
        for (File resultDir : files) {
            if (!resultDir.isDirectory()) {
                continue;
            }
            // Only include if it contain results file
            File resultFile = new File(resultDir, TEST_RESULT_FILE_NAME);
            if (!resultFile.exists()) {
                continue;
            }
            directoryList.add(resultDir);
        }
        Collections.sort(directoryList, (d1, d2) -> d1.getName().compareTo(d2.getName()));
        return directoryList;
    }

    /**
     * Return the given time as a {@link String} suitable for displaying.
     * <p/>
     * Example: Fri Aug 20 15:13:03 PDT 2010
     *
     * @param time the epoch time in ms since midnight Jan 1, 1970
     */
    static String toReadableDateString(long time) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");
        return dateFormat.format(new Date(time));
    }

    /**
     * When nullable is null, return an empty string. Otherwise, return the value in nullable.
     */
    private static String nullToEmpty(String nullable) {
        return nullable == null ? "" : nullable;
    }
}
