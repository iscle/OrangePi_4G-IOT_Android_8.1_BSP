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
 * limitations under the License
 */
package com.android.compatibility.common.util;

import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.AbiUtils;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for {@link ResultHandler}
 */
public class ResultHandlerTest extends TestCase {

    private static final String SUITE_NAME = "CTS";
    private static final String SUITE_VERSION = "5.0";
    private static final String SUITE_PLAN = "cts";
    private static final String SUITE_BUILD = "12345";
    private static final String REPORT_VERSION = "5.0";
    private static final String OS_NAME = System.getProperty("os.name");
    private static final String OS_VERSION = System.getProperty("os.version");
    private static final String OS_ARCH = System.getProperty("os.arch");
    private static final String JAVA_VENDOR = System.getProperty("java.vendor");
    private static final String JAVA_VERSION = System.getProperty("java.version");
    private static final String NAME_A = "ModuleA";
    private static final String NAME_B = "ModuleB";
    private static final String DONE_A = "false";
    private static final String DONE_B = "true";
    private static final String RUNTIME_A = "100";
    private static final String RUNTIME_B = "200";
    private static final String ABI = "mips64";
    private static final String ID_A = AbiUtils.createId(ABI, NAME_A);
    private static final String ID_B = AbiUtils.createId(ABI, NAME_B);

    private static final String BUILD_ID = "build_id";
    private static final String BUILD_PRODUCT = "build_product";
    private static final String EXAMPLE_BUILD_ID = "XYZ";
    private static final String EXAMPLE_BUILD_PRODUCT = "wolverine";

    private static final String DEVICE_A = "device123";
    private static final String DEVICE_B = "device456";
    private static final String DEVICES = "device456,device123";
    private static final String CLASS_A = "android.test.Foor";
    private static final String CLASS_B = "android.test.Bar";
    private static final String METHOD_1 = "testBlah1";
    private static final String METHOD_2 = "testBlah2";
    private static final String METHOD_3 = "testBlah3";
    private static final String METHOD_4 = "testBlah4";
    private static final String METHOD_5 = "testBlah5";
    private static final String SUMMARY_SOURCE = String.format("%s#%s:20", CLASS_B, METHOD_4);
    private static final String SUMMARY_MESSAGE = "Headline";
    private static final double SUMMARY_VALUE = 9001;
    private static final String MESSAGE = "Something small is not alright";
    private static final String STACK_TRACE = "Something small is not alright\n " +
            "at four.big.insects.Marley.sing(Marley.java:10)";
    private static final String BUG_REPORT = "https://cnsviewer.corp.google.com/cns/bugreport.txt";
    private static final String LOGCAT = "https://cnsviewer.corp.google.com/cns/logcat.gz";
    private static final String SCREENSHOT = "https://cnsviewer.corp.google.com/screenshot.png";
    private static final long START_MS = 1431586801000L;
    private static final long END_MS = 1431673199000L;
    private static final String START_DISPLAY = "Fri Aug 20 15:13:03 PDT 2010";
    private static final String END_DISPLAY = "Fri Aug 20 15:13:04 PDT 2010";

    private static final String REFERENCE_URL="http://android.com";
    private static final String LOG_URL ="file:///path/to/logs";
    private static final String COMMAND_LINE_ARGS = "cts -m CtsMyModuleTestCases";
    private static final String XML_BASE =
            "<?xml version='1.0' encoding='UTF-8' standalone='no' ?>" +
            "<?xml-stylesheet type=\"text/xsl\" href=\"compatibility_result.xsl\"?>\n" +
            "<Result start=\"%d\" end=\"%d\" start_display=\"%s\"" +
            "end_display=\"%s\" suite_name=\"%s\" suite_version=\"%s\" " +
            "suite_plan=\"%s\" suite_build_number=\"%s\" report_version=\"%s\" " +
            "devices=\"%s\" host_name=\"%s\"" +
            "os_name=\"%s\" os_version=\"%s\" os_arch=\"%s\" java_vendor=\"%s\"" +
            "java_version=\"%s\" reference_url=\"%s\" log_url=\"%s\"" +
            "command_line_args=\"%s\">\n" +
            "%s%s%s" +
            "</Result>";
    private static final String XML_BUILD_INFO =
            "  <Build build_fingerprint=\"%s\" " + BUILD_ID + "=\"%s\" " +
               BUILD_PRODUCT + "=\"%s\" />\n";
    private static final String XML_SUMMARY =
            "  <Summary pass=\"%d\" failed=\"%d\" " +
            "modules_done=\"1\" modules_total=\"1\" />\n";
    private static final String XML_MODULE =
            "  <Module name=\"%s\" abi=\"%s\" device=\"%s\" runtime=\"%s\" done=\"%s\">\n" +
            "%s" +
            "  </Module>\n";
    private static final String XML_CASE =
            "    <TestCase name=\"%s\">\n" +
            "%s" +
            "    </TestCase>\n";
    private static final String XML_TEST_PASS =
            "      <Test result=\"pass\" name=\"%s\"/>\n";
    private static final String XML_TEST_SKIP =
            "      <Test result=\"pass\" name=\"%s\" skipped=\"true\"/>\n";
    private static final String XML_TEST_FAIL =
            "      <Test result=\"fail\" name=\"%s\">\n" +
            "        <Failure message=\"%s\">\n" +
            "          <StackTrace>%s</StackTrace>\n" +
            "        </Failure>\n" +
            "        <BugReport>%s</BugReport>\n" +
            "        <Logcat>%s</Logcat>\n" +
            "        <Screenshot>%s</Screenshot>\n" +
            "      </Test>\n";
    private static final String XML_TEST_RESULT =
            "      <Test result=\"pass\" name=\"%s\">\n" +
            "        <Summary>\n" +
            "          <Metric source=\"%s\" message=\"%s\" score_type=\"%s\" score_unit=\"%s\">\n" +
            "             <Value>%s</Value>\n" +
            "          </Metric>\n" +
            "        </Summary>\n" +
            "      </Test>\n";
    private File resultsDir = null;
    private File resultDir = null;

    @Override
    public void setUp() throws Exception {
        resultsDir = FileUtil.createTempDir("results");
        resultDir = FileUtil.createTempDir("12345", resultsDir);
    }

    @Override
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(resultsDir);
    }

    public void testSerialization() throws Exception {
        IInvocationResult result = new InvocationResult();
        result.setStartTime(START_MS);
        result.setTestPlan(SUITE_PLAN);
        result.addDeviceSerial(DEVICE_A);
        result.addDeviceSerial(DEVICE_B);
        result.addInvocationInfo(BUILD_ID, EXAMPLE_BUILD_ID);
        result.addInvocationInfo(BUILD_PRODUCT, EXAMPLE_BUILD_PRODUCT);
        // Module A: test1 passes, test2 not executed
        IModuleResult moduleA = result.getOrCreateModule(ID_A);
        moduleA.setDone(false);
        moduleA.addRuntime(Integer.parseInt(RUNTIME_A));
        ICaseResult moduleACase = moduleA.getOrCreateResult(CLASS_A);
        ITestResult moduleATest1 = moduleACase.getOrCreateResult(METHOD_1);
        moduleATest1.setResultStatus(TestStatus.PASS);
        ITestResult moduleATest2 = moduleACase.getOrCreateResult(METHOD_2);
        moduleATest2.setResultStatus(null); // not executed test
        // Module B: test3 fails, test4 passes with report log, test5 passes with skip
        IModuleResult moduleB = result.getOrCreateModule(ID_B);
        moduleB.setDone(true);
        moduleB.addRuntime(Integer.parseInt(RUNTIME_B));
        ICaseResult moduleBCase = moduleB.getOrCreateResult(CLASS_B);
        ITestResult moduleBTest3 = moduleBCase.getOrCreateResult(METHOD_3);
        moduleBTest3.setResultStatus(TestStatus.FAIL);
        moduleBTest3.setMessage(MESSAGE);
        moduleBTest3.setStackTrace(STACK_TRACE);
        moduleBTest3.setBugReport(BUG_REPORT);
        moduleBTest3.setLog(LOGCAT);
        moduleBTest3.setScreenshot(SCREENSHOT);
        ITestResult moduleBTest4 = moduleBCase.getOrCreateResult(METHOD_4);
        moduleBTest4.setResultStatus(TestStatus.PASS);
        ReportLog report = new ReportLog();
        ReportLog.Metric summary = new ReportLog.Metric(SUMMARY_SOURCE, SUMMARY_MESSAGE,
                SUMMARY_VALUE, ResultType.HIGHER_BETTER, ResultUnit.SCORE);
        report.setSummary(summary);
        moduleBTest4.setReportLog(report);
        ITestResult moduleBTest5 = moduleBCase.getOrCreateResult(METHOD_5);
        moduleBTest5.skipped();

        // Serialize to file
        ResultHandler.writeResults(SUITE_NAME, SUITE_VERSION, SUITE_PLAN, SUITE_BUILD,
                result, resultDir, START_MS, END_MS, REFERENCE_URL, LOG_URL,
                COMMAND_LINE_ARGS);

        // Parse the results and assert correctness
        checkResult(ResultHandler.getResultFromDir(resultDir));
    }

    public void testParsing() throws Exception {
        File resultDir = writeResultDir(resultsDir);
        // Parse the results and assert correctness
        checkResult(ResultHandler.getResultFromDir(resultDir));
    }

    public void testGetLightResults() throws Exception {
        File resultDir = writeResultDir(resultsDir);
        List<IInvocationResult> lightResults = ResultHandler.getLightResults(resultsDir);
        assertEquals("Expected one result", 1, lightResults.size());
        IInvocationResult lightResult = lightResults.get(0);
        checkLightResult(lightResult);
    }

    /*
     * Helper to write a result to the results dir, for testing.
     * @return the written resultDir
     */
    static File writeResultDir(File resultsDir) throws IOException {
        File resultDir = null;
        FileWriter writer = null;
        try {
            resultDir = FileUtil.createTempDir("12345", resultsDir);
            // Create the result file
            File resultFile = new File(resultDir, ResultHandler.TEST_RESULT_FILE_NAME);
            writer = new FileWriter(resultFile);
            String buildInfo = String.format(XML_BUILD_INFO, DEVICE_A,
                    EXAMPLE_BUILD_ID, EXAMPLE_BUILD_PRODUCT);
            String summary = String.format(XML_SUMMARY, 2, 1);
            String moduleATest = String.format(XML_TEST_PASS, METHOD_1);
            String moduleACases = String.format(XML_CASE, CLASS_A, moduleATest);
            String moduleA = String.format(XML_MODULE, NAME_A, ABI, DEVICE_A, RUNTIME_A, DONE_A,
                    moduleACases);
            String moduleBTest3 = String.format(XML_TEST_FAIL, METHOD_3, MESSAGE, STACK_TRACE,
                    BUG_REPORT, LOGCAT, SCREENSHOT);
            String moduleBTest4 = String.format(XML_TEST_RESULT, METHOD_4, SUMMARY_SOURCE,
                    SUMMARY_MESSAGE, ResultType.HIGHER_BETTER.toReportString(),
                    ResultUnit.SCORE.toReportString(), Double.toString(SUMMARY_VALUE));
            String moduleBTest5 = String.format(XML_TEST_SKIP, METHOD_5);
            String moduleBTests = String.join("", moduleBTest3, moduleBTest4, moduleBTest5);
            String moduleBCases = String.format(XML_CASE, CLASS_B, moduleBTests);
            String moduleB = String.format(XML_MODULE, NAME_B, ABI, DEVICE_B, RUNTIME_B, DONE_B,
                    moduleBCases);
            String modules = String.join("", moduleA, moduleB);
            String hostName = "";
            try {
                hostName = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException ignored) {}
            String output = String.format(XML_BASE, START_MS, END_MS, START_DISPLAY, END_DISPLAY,
                    SUITE_NAME, SUITE_VERSION, SUITE_PLAN, SUITE_BUILD, REPORT_VERSION, DEVICES,
                    hostName, OS_NAME, OS_VERSION, OS_ARCH, JAVA_VENDOR,
                    JAVA_VERSION, REFERENCE_URL, LOG_URL, COMMAND_LINE_ARGS,
                    buildInfo, summary, modules);
            writer.write(output);
            writer.flush();
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        return resultDir;
    }

    static void checkLightResult(IInvocationResult lightResult) throws Exception {
        assertEquals("Expected 3 passes", 3, lightResult.countResults(TestStatus.PASS));
        assertEquals("Expected 1 failure", 1, lightResult.countResults(TestStatus.FAIL));

        Map<String, String> buildInfo = lightResult.getInvocationInfo();
        assertEquals("Incorrect Build ID", EXAMPLE_BUILD_ID, buildInfo.get(BUILD_ID));
        assertEquals("Incorrect Build Product",
            EXAMPLE_BUILD_PRODUCT, buildInfo.get(BUILD_PRODUCT));

        Set<String> serials = lightResult.getDeviceSerials();
        assertTrue("Missing device", serials.contains(DEVICE_A));
        assertTrue("Missing device", serials.contains(DEVICE_B));
        assertEquals("Expected 2 devices", 2, serials.size());
        assertTrue("Incorrect devices", serials.contains(DEVICE_A) && serials.contains(DEVICE_B));
        assertEquals("Incorrect start time", START_MS, lightResult.getStartTime());
        assertEquals("Incorrect test plan", SUITE_PLAN, lightResult.getTestPlan());
        List<IModuleResult> modules = lightResult.getModules();
        assertEquals("Expected 1 completed module", 1, lightResult.getModuleCompleteCount());
        assertEquals("Expected 2 total modules", 2, modules.size());
    }

    static void checkResult(IInvocationResult result) throws Exception {
        assertEquals("Expected 3 passes", 3, result.countResults(TestStatus.PASS));
        assertEquals("Expected 1 failure", 1, result.countResults(TestStatus.FAIL));

        Map<String, String> buildInfo = result.getInvocationInfo();
        assertEquals("Incorrect Build ID", EXAMPLE_BUILD_ID, buildInfo.get(BUILD_ID));
        assertEquals("Incorrect Build Product",
            EXAMPLE_BUILD_PRODUCT, buildInfo.get(BUILD_PRODUCT));

        Set<String> serials = result.getDeviceSerials();
        assertTrue("Missing device", serials.contains(DEVICE_A));
        assertTrue("Missing device", serials.contains(DEVICE_B));
        assertEquals("Expected 2 devices", 2, serials.size());
        assertTrue("Incorrect devices", serials.contains(DEVICE_A) && serials.contains(DEVICE_B));
        assertEquals("Incorrect start time", START_MS, result.getStartTime());
        assertEquals("Incorrect test plan", SUITE_PLAN, result.getTestPlan());

        List<IModuleResult> modules = result.getModules();
        assertEquals("Expected 2 modules", 2, modules.size());

        IModuleResult moduleA = modules.get(0);
        assertEquals("Expected 1 pass", 1, moduleA.countResults(TestStatus.PASS));
        assertEquals("Expected 0 failures", 0, moduleA.countResults(TestStatus.FAIL));
        assertEquals("Incorrect ABI", ABI, moduleA.getAbi());
        assertEquals("Incorrect name", NAME_A, moduleA.getName());
        assertEquals("Incorrect ID", ID_A, moduleA.getId());
        assertEquals("Incorrect runtime", Integer.parseInt(RUNTIME_A), moduleA.getRuntime());
        List<ICaseResult> moduleACases = moduleA.getResults();
        assertEquals("Expected 1 test case", 1, moduleACases.size());
        ICaseResult moduleACase = moduleACases.get(0);
        assertEquals("Incorrect name", CLASS_A, moduleACase.getName());
        List<ITestResult> moduleAResults = moduleACase.getResults();
        assertEquals("Expected 1 result", 1, moduleAResults.size());
        ITestResult moduleATest1 = moduleAResults.get(0);
        assertEquals("Incorrect name", METHOD_1, moduleATest1.getName());
        assertEquals("Incorrect result", TestStatus.PASS, moduleATest1.getResultStatus());
        assertNull("Unexpected bugreport", moduleATest1.getBugReport());
        assertNull("Unexpected log", moduleATest1.getLog());
        assertNull("Unexpected screenshot", moduleATest1.getScreenshot());
        assertNull("Unexpected message", moduleATest1.getMessage());
        assertNull("Unexpected stack trace", moduleATest1.getStackTrace());
        assertNull("Unexpected report", moduleATest1.getReportLog());

        IModuleResult moduleB = modules.get(1);
        assertEquals("Expected 2 passes", 2, moduleB.countResults(TestStatus.PASS));
        assertEquals("Expected 1 failure", 1, moduleB.countResults(TestStatus.FAIL));
        assertEquals("Incorrect ABI", ABI, moduleB.getAbi());
        assertEquals("Incorrect name", NAME_B, moduleB.getName());
        assertEquals("Incorrect ID", ID_B, moduleB.getId());
        assertEquals("Incorrect runtime", Integer.parseInt(RUNTIME_B), moduleB.getRuntime());
        List<ICaseResult> moduleBCases = moduleB.getResults();
        assertEquals("Expected 1 test case", 1, moduleBCases.size());
        ICaseResult moduleBCase = moduleBCases.get(0);
        assertEquals("Incorrect name", CLASS_B, moduleBCase.getName());
        List<ITestResult> moduleBResults = moduleBCase.getResults();
        assertEquals("Expected 3 results", 3, moduleBResults.size());
        ITestResult moduleBTest3 = moduleBResults.get(0);
        assertEquals("Incorrect name", METHOD_3, moduleBTest3.getName());
        assertEquals("Incorrect result", TestStatus.FAIL, moduleBTest3.getResultStatus());
        assertEquals("Incorrect bugreport", BUG_REPORT, moduleBTest3.getBugReport());
        assertEquals("Incorrect log", LOGCAT, moduleBTest3.getLog());
        assertEquals("Incorrect screenshot", SCREENSHOT, moduleBTest3.getScreenshot());
        assertEquals("Incorrect message", MESSAGE, moduleBTest3.getMessage());
        assertEquals("Incorrect stack trace", STACK_TRACE, moduleBTest3.getStackTrace());
        assertNull("Unexpected report", moduleBTest3.getReportLog());
        ITestResult moduleBTest4 = moduleBResults.get(1);
        assertEquals("Incorrect name", METHOD_4, moduleBTest4.getName());
        assertEquals("Incorrect result", TestStatus.PASS, moduleBTest4.getResultStatus());
        assertNull("Unexpected bugreport", moduleBTest4.getBugReport());
        assertNull("Unexpected log", moduleBTest4.getLog());
        assertNull("Unexpected screenshot", moduleBTest4.getScreenshot());
        assertNull("Unexpected message", moduleBTest4.getMessage());
        assertNull("Unexpected stack trace", moduleBTest4.getStackTrace());
        ReportLog report = moduleBTest4.getReportLog();
        assertNotNull("Expected report", report);
        ReportLog.Metric summary = report.getSummary();
        assertNotNull("Expected report summary", summary);
        assertEquals("Incorrect source", SUMMARY_SOURCE, summary.getSource());
        assertEquals("Incorrect message", SUMMARY_MESSAGE, summary.getMessage());
        assertEquals("Incorrect type", ResultType.HIGHER_BETTER, summary.getType());
        assertEquals("Incorrect unit", ResultUnit.SCORE, summary.getUnit());
        assertTrue("Incorrect values", Arrays.equals(new double[] { SUMMARY_VALUE },
                summary.getValues()));
        ITestResult moduleBTest5 = moduleBResults.get(2);
        assertEquals("Incorrect name", METHOD_5, moduleBTest5.getName());
        assertEquals("Incorrect result", TestStatus.PASS, moduleBTest5.getResultStatus());
        assertTrue("Expected skipped", moduleBTest5.isSkipped());
        assertNull("Unexpected bugreport", moduleBTest5.getBugReport());
        assertNull("Unexpected log", moduleBTest5.getLog());
        assertNull("Unexpected screenshot", moduleBTest5.getScreenshot());
        assertNull("Unexpected message", moduleBTest5.getMessage());
        assertNull("Unexpected stack trace", moduleBTest5.getStackTrace());
        assertNull("Unexpected report", moduleBTest5.getReportLog());
    }
}
