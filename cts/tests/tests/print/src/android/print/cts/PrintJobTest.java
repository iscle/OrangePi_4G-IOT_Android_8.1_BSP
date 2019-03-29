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

package android.print.cts;

import android.print.PrintAttributes;
import android.print.PrintAttributes.Margins;
import android.print.PrintAttributes.MediaSize;
import android.print.PrintAttributes.Resolution;
import android.print.PrintDocumentAdapter;
import android.print.PrintJobInfo;
import android.print.PrintManager;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.cts.services.CustomPrintOptionsActivity;
import android.print.cts.services.FirstPrintService;
import android.print.cts.services.PrintServiceCallbacks;
import android.print.cts.services.PrinterDiscoverySessionCallbacks;
import android.print.cts.services.SecondPrintService;
import android.print.cts.services.StubbablePrinterDiscoverySession;
import android.printservice.PrintJob;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

import static android.print.cts.Utils.eventually;
import static org.junit.Assert.*;

/**
 * Tests all possible states of print jobs.
 */
@RunWith(AndroidJUnit4.class)
public class PrintJobTest extends BasePrintTest {
    private static final String PRINTER_NAME = "TestPrinter";

    private final static String VALID_NULL_KEY = "validNullKey";
    private final static String VALID_STRING_KEY = "validStringKey";
    private final static String STRING_VALUE = "string value";
    private final static String INVALID_STRING_KEY = "invalidStringKey";
    private final static String VALID_INT_KEY = "validIntKey";
    private final static int INT_VALUE = 23;
    private final static String INVALID_INT_KEY = "invalidIntKey";

    private final boolean testSuccess[] = new boolean[1];

    /** The printer discovery session used in this test */
    private static StubbablePrinterDiscoverySession sDiscoverySession;
    private static boolean sHasBeenSetUp;

    /**
     * Create a mock {@link PrinterDiscoverySessionCallbacks} that discovers a simple test printer.
     *
     * @return The mock session callbacks
     */
    private PrinterDiscoverySessionCallbacks createFirstMockPrinterDiscoverySessionCallbacks() {
        return createMockPrinterDiscoverySessionCallbacks(invocation -> {
            // Get the session.
            sDiscoverySession = ((PrinterDiscoverySessionCallbacks) invocation.getMock())
                    .getSession();

            if (sDiscoverySession.getPrinters().isEmpty()) {
                PrinterId printerId =
                        sDiscoverySession.getService().generatePrinterId(PRINTER_NAME);
                PrinterInfo.Builder printer = new PrinterInfo.Builder(
                        sDiscoverySession.getService().generatePrinterId(PRINTER_NAME),
                        PRINTER_NAME, PrinterInfo.STATUS_IDLE);

                printer.setCapabilities(new PrinterCapabilitiesInfo.Builder(printerId)
                        .addMediaSize(MediaSize.ISO_A4, true)
                        .addResolution(new Resolution("300x300", "300dpi", 300, 300), true)
                        .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                PrintAttributes.COLOR_MODE_COLOR)
                        .setMinMargins(new Margins(0, 0, 0, 0)).build());

                ArrayList<PrinterInfo> printers = new ArrayList<>(1);
                printers.add(printer.build());

                sDiscoverySession.addPrinters(printers);
            }
            return null;
        }, null, null, invocation -> null, null, null, invocation -> {
            // Take a note onDestroy was called.
            onPrinterDiscoverySessionDestroyCalled();
            return null;
        });
    }

    private interface PrintJobTestFn {
        void onPrintJobQueued(PrintJob printJob) throws Throwable;
    }

    /**
     * Create mock service callback for a session. Once the job is queued the test function is
     * called.
     *
     * @param sessionCallbacks The callbacks of the session
     * @param printJobTest test function to call
     */
    private PrintServiceCallbacks createFirstMockPrinterServiceCallbacks(
            final PrinterDiscoverySessionCallbacks sessionCallbacks,
            final PrintJobTestFn printJobTest) {
        return createMockPrintServiceCallbacks(
                invocation -> sessionCallbacks, invocation -> {
                    PrintJob printJob = (PrintJob) invocation.getArguments()[0];

                    try {
                        printJobTest.onPrintJobQueued(printJob);
                        testSuccess[0] = true;
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }

                    onPrintJobQueuedCalled();

                    return null;
                }, null);
    }

    /**
     * Base test for the print job tests. Starts a print job and executes a testFn once the job is
     * queued.
     *
     * @throws Exception If anything is unexpected.
     */
    private void baseTest(PrintJobTestFn testFn)
            throws Exception {
        testSuccess[0] = false;

        // Create the session of the printers that we will be checking.
        PrinterDiscoverySessionCallbacks sessionCallbacks
                = createFirstMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createFirstMockPrinterServiceCallbacks(
                sessionCallbacks, testFn);

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // We don't use the second service, but we have to still configure it
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

        // Start printing.
        print(adapter);
        clickPrintButton();

        // Wait for print job to be queued
        waitForServiceOnPrintJobQueuedCallbackCalled(1);

        // Wait for discovery session to be destroyed to isolate tests from each other
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        if (!testSuccess[0]) {
            throw new Exception("Did not succeed");
        }
    }

    private static void checkState(PrintJob job, int state) throws Throwable {
        eventually(() -> assertEquals(state, job.getInfo().getState()));
        switch (state) {
            case PrintJobInfo.STATE_QUEUED:
                eventually(() -> assertTrue(job.isQueued()));
                break;
            case PrintJobInfo.STATE_STARTED:
                eventually(() -> assertTrue(job.isStarted()));
                break;
            case PrintJobInfo.STATE_BLOCKED:
                eventually(() -> assertTrue(job.isBlocked()));
                break;
            case PrintJobInfo.STATE_COMPLETED:
                eventually(() -> assertTrue(job.isCompleted()));
                break;
            case PrintJobInfo.STATE_FAILED:
                eventually(() -> assertTrue(job.isFailed()));
                break;
            case PrintJobInfo.STATE_CANCELED:
                eventually(() -> assertTrue(job.isCancelled()));
                break;
            default:
                // not reached
                throw new IllegalArgumentException("Cannot check " + state);
        }
    }

    @Before
    public void setPrinter() throws Exception {
        if (!sHasBeenSetUp) {
            resetCounters();
            PrinterDiscoverySessionCallbacks sessionCallbacks
                    = createFirstMockPrinterDiscoverySessionCallbacks();

            // Create the service callbacks for the first print service.
            PrintServiceCallbacks serviceCallbacks = createFirstMockPrinterServiceCallbacks(
                    sessionCallbacks, printJob -> { });

            // Configure the print services.
            FirstPrintService.setCallbacks(serviceCallbacks);

            // We don't use the second service, but we have to still configure it
            SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

            // Create a print adapter that respects the print contract.
            PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

            makeDefaultPrinter(adapter, PRINTER_NAME);

            sHasBeenSetUp = true;
        }

        resetCounters();
    }

    @Test
    public void blockWithReason() throws Exception {
        baseTest(printJob -> {
            printJob.start();
            checkState(printJob, PrintJobInfo.STATE_STARTED);

            printJob.setStatus(R.string.testStr1);
            eventually(() -> assertEquals(getActivity().getString(R.string.testStr1),
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));

            boolean success = printJob.block("test reason");
            assertTrue(success);
            checkState(printJob, PrintJobInfo.STATE_BLOCKED);
            eventually(() -> assertEquals("test reason",
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));

            success = printJob.block("another reason");
            assertFalse(success);
            checkState(printJob, PrintJobInfo.STATE_BLOCKED);
            eventually(() -> assertEquals("test reason",
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));

            printJob.setStatus(R.string.testStr2);
            eventually(() -> assertEquals(getActivity().getString(R.string.testStr2),
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));
        });
    }

    @Test
    public void failWithReason() throws Exception {
        baseTest(printJob -> {
            printJob.start();
            checkState(printJob, PrintJobInfo.STATE_STARTED);

            boolean success = printJob.fail("test reason");
            assertTrue(success);
            checkState(printJob, PrintJobInfo.STATE_FAILED);
            eventually(() -> assertEquals("test reason",
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));

            success = printJob.fail("another reason");
            assertFalse(success);
            checkState(printJob, PrintJobInfo.STATE_FAILED);
            eventually(() -> assertEquals("test reason",
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));
        });
    }

    @Test
    public void tag() throws Exception {
        baseTest(printJob -> {
            // Default value should be null
            assertNull(printJob.getTag());

            printJob.setTag("testTag");
            eventually(() -> assertEquals("testTag", printJob.getTag()));

            printJob.setTag(null);
            eventually(() -> assertNull(printJob.getTag()));
        });
    }

    @Test
    public void advancedOption() throws Exception {
        testSuccess[0] = false;

        // Create the session of the printers that we will be checking.
        PrinterDiscoverySessionCallbacks sessionCallbacks
                = createFirstMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createFirstMockPrinterServiceCallbacks(
                sessionCallbacks, printJob -> {
                    assertTrue(printJob.hasAdvancedOption(VALID_STRING_KEY));
                    assertEquals(STRING_VALUE, printJob.getAdvancedStringOption(VALID_STRING_KEY));

                    assertFalse(printJob.hasAdvancedOption(INVALID_STRING_KEY));
                    assertNull(printJob.getAdvancedStringOption(INVALID_STRING_KEY));

                    assertTrue(printJob.hasAdvancedOption(VALID_INT_KEY));
                    assertEquals(INT_VALUE, printJob.getAdvancedIntOption(VALID_INT_KEY));

                    assertTrue(printJob.hasAdvancedOption(VALID_NULL_KEY));
                    assertNull(printJob.getAdvancedStringOption(VALID_NULL_KEY));

                    assertFalse(printJob.hasAdvancedOption(INVALID_INT_KEY));
                    assertEquals(0, printJob.getAdvancedIntOption(INVALID_INT_KEY));

                    assertNull(printJob.getAdvancedStringOption(VALID_INT_KEY));
                    assertEquals(0, printJob.getAdvancedIntOption(VALID_STRING_KEY));
                });

        final int[] callCount = new int[1];

        CustomPrintOptionsActivity.setCallBack(
                (printJob, printer) -> {
                    if (callCount[0] == 0) {
                        PrintJobInfo.Builder printJobBuilder = new PrintJobInfo.Builder(printJob);

                        try {
                            printJobBuilder.putAdvancedOption(null, STRING_VALUE);
                            throw new RuntimeException("Should not be able to use a null key");
                        } catch (NullPointerException e) {
                            // expected
                        }

                        // Second put overrides the first
                        printJobBuilder.putAdvancedOption(VALID_STRING_KEY, "something");
                        printJobBuilder.putAdvancedOption(VALID_STRING_KEY, STRING_VALUE);

                        printJobBuilder.putAdvancedOption(VALID_INT_KEY, "something");
                        printJobBuilder.putAdvancedOption(VALID_INT_KEY, INT_VALUE);

                        printJobBuilder.putAdvancedOption(VALID_NULL_KEY, null);

                        // Rotate the media size to force adapter to write again
                        PrintAttributes.Builder attributeBuilder = new PrintAttributes.Builder();
                        attributeBuilder.setMediaSize(printJob.getAttributes().getMediaSize()
                                .asLandscape());
                        attributeBuilder.setResolution(printJob.getAttributes().getResolution());
                        attributeBuilder.setDuplexMode(printJob.getAttributes().getDuplexMode());
                        attributeBuilder.setColorMode(printJob.getAttributes().getColorMode());
                        attributeBuilder.setMinMargins(printJob.getAttributes().getMinMargins());

                        printJobBuilder.setAttributes(attributeBuilder.build());

                        return printJobBuilder.build();
                    } else {
                        // Check that options are readable
                        assertTrue(printJob.hasAdvancedOption(VALID_STRING_KEY));
                        assertEquals(STRING_VALUE,
                                printJob.getAdvancedStringOption(VALID_STRING_KEY));

                        assertFalse(printJob.hasAdvancedOption(INVALID_STRING_KEY));
                        assertNull(printJob.getAdvancedStringOption(INVALID_STRING_KEY));

                        assertTrue(printJob.hasAdvancedOption(VALID_INT_KEY));
                        assertEquals(INT_VALUE, printJob.getAdvancedIntOption(VALID_INT_KEY));

                        assertTrue(printJob.hasAdvancedOption(VALID_NULL_KEY));
                        assertNull(printJob.getAdvancedStringOption(VALID_NULL_KEY));

                        assertFalse(printJob.hasAdvancedOption(INVALID_INT_KEY));
                        assertEquals(0, printJob.getAdvancedIntOption(INVALID_INT_KEY));

                        assertNull(printJob.getAdvancedStringOption(VALID_INT_KEY));
                        assertEquals(0, printJob.getAdvancedIntOption(VALID_STRING_KEY));

                        return null;
                    }
                });

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // We don't use the second service, but we have to still configure it
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

        // Start printing.
        print(adapter, "advancedOption");

        waitForWriteAdapterCallback(1);

        openPrintOptions();
        openCustomPrintOptions();

        waitForWriteAdapterCallback(2);

        callCount[0]++;

        // The advanced option should not be readable from the activity
        getActivity().getSystemService(PrintManager.class).getPrintJobs().stream()
                .filter(printJob -> printJob.getInfo().getLabel().equals("advancedOption"))
                .forEach(printJob -> {
                    assertFalse(printJob.getInfo().hasAdvancedOption(VALID_STRING_KEY));
                    assertEquals(null,
                            printJob.getInfo().getAdvancedStringOption(VALID_STRING_KEY));

                    assertFalse(printJob.getInfo().hasAdvancedOption(INVALID_STRING_KEY));
                    assertNull(printJob.getInfo().getAdvancedStringOption(INVALID_STRING_KEY));

                    assertFalse(printJob.getInfo().hasAdvancedOption(VALID_INT_KEY));
                    assertEquals(0, printJob.getInfo().getAdvancedIntOption(VALID_INT_KEY));

                    assertFalse(printJob.getInfo().hasAdvancedOption(VALID_NULL_KEY));
                    assertNull(printJob.getInfo().getAdvancedStringOption(VALID_NULL_KEY));

                    assertFalse(printJob.getInfo().hasAdvancedOption(INVALID_INT_KEY));
                    assertEquals(0, printJob.getInfo().getAdvancedIntOption(INVALID_INT_KEY));

                    assertNull(printJob.getInfo().getAdvancedStringOption(VALID_INT_KEY));
                    assertEquals(0, printJob.getInfo().getAdvancedIntOption(VALID_STRING_KEY));
                });

        openCustomPrintOptions();
        clickPrintButton();

        // Wait for print job to be queued
        waitForServiceOnPrintJobQueuedCallbackCalled(1);

        // Wait for discovery session to be destroyed to isolate tests from each other
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        if (!testSuccess[0]) {
            throw new Exception("Did not succeed");
        }
    }

    @Test
    public void other() throws Exception {
        baseTest(printJob -> {
            assertNotNull(printJob.getDocument());
            assertNotNull(printJob.getId());
        });
    }

    @Test
    public void setStatus() throws Exception {
        baseTest(printJob -> {
            printJob.start();

            printJob.setStatus(R.string.testStr1);
            eventually(() -> assertEquals(getActivity().getString(R.string.testStr1),
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));

            printJob.setStatus("testStr3");
            eventually(() -> assertEquals("testStr3",
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));

            printJob.setStatus(R.string.testStr2);
            eventually(() -> assertEquals(getActivity().getString(R.string.testStr2),
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));

            printJob.setStatus(null);
            eventually(() -> assertNull(
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));

            printJob.block("testStr4");
            eventually(() -> assertEquals("testStr4",
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));

            printJob.setStatus(R.string.testStr2);
            eventually(() -> assertEquals(getActivity().getString(R.string.testStr2),
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));

            printJob.setStatus(0);
            eventually(() -> assertNull(
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));

            printJob.setStatus("testStr3");
            eventually(() -> assertEquals("testStr3",
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));

            printJob.setStatus(-1);
            eventually(() -> assertNull(
                    printJob.getInfo().getStatus(getActivity().getPackageManager())));
        });
    }
}
