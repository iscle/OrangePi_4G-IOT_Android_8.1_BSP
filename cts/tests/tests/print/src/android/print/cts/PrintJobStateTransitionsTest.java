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

import static android.print.cts.Utils.eventually;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.print.PrintAttributes;
import android.print.PrintAttributes.Margins;
import android.print.PrintAttributes.MediaSize;
import android.print.PrintAttributes.Resolution;
import android.print.PrintDocumentAdapter;
import android.print.PrintJobInfo;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.cts.services.FirstPrintService;
import android.print.cts.services.PrintServiceCallbacks;
import android.print.cts.services.PrinterDiscoverySessionCallbacks;
import android.print.cts.services.SecondPrintService;
import android.print.cts.services.StubbablePrinterDiscoverySession;
import android.printservice.PrintJob;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Tests all possible states of print jobs.
 */
@RunWith(Parameterized.class)
public class PrintJobStateTransitionsTest extends BasePrintTest {
    private static final String PRINTER_NAME = "TestPrinter";
    private static final String LOG_TAG = "PrintJobStateTransTest";

    /** The printer discovery session used in this test */
    private static StubbablePrinterDiscoverySession sDiscoverySession;
    private static boolean sHasBeenSetUp;

    private final static int STATES[] = new int[] { PrintJobInfo.STATE_QUEUED,
            PrintJobInfo.STATE_STARTED,
            PrintJobInfo.STATE_BLOCKED,
            PrintJobInfo.STATE_COMPLETED,
            PrintJobInfo.STATE_FAILED,
            PrintJobInfo.STATE_CANCELED
    };

    private final static boolean sKnownFailures[][] = new boolean[8][8];

    private final int mState1;
    private final int mState2;
    private final int mState3;
    private final boolean[] mTestSuccess = new boolean[1];

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
                        mTestSuccess[0] = true;
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }

                    onPrintJobQueuedCalled();

                    return null;
                }, null);
    }

    public PrintJobStateTransitionsTest(int state1, int state2, int state3) {
        mState1 = state1;
        mState2 = state2;
        mState3 = state3;
    }

    private static boolean setState(PrintJob job, int state) {
        switch (state) {
            case PrintJobInfo.STATE_QUEUED:
                // queue cannot be set, but is set at the beginning
                return job.isQueued();
            case PrintJobInfo.STATE_STARTED:
                return job.start();
            case PrintJobInfo.STATE_BLOCKED:
                return job.block(null);
            case PrintJobInfo.STATE_COMPLETED:
                return job.complete();
            case PrintJobInfo.STATE_FAILED:
                return job.fail(null);
            case PrintJobInfo.STATE_CANCELED:
                return job.cancel();
            default:
                // not reached
                throw new IllegalArgumentException("Cannot switch to " + state);
        }
    }

    private static boolean isStateTransitionAllowed(int before, int after) {
        switch (before) {
            case PrintJobInfo.STATE_QUEUED:
                switch (after) {
                    case PrintJobInfo.STATE_QUEUED:
                        // queued is not actually set, see setState
                    case PrintJobInfo.STATE_STARTED:
                    case PrintJobInfo.STATE_FAILED:
                    case PrintJobInfo.STATE_CANCELED:
                        return true;
                    default:
                        return false;
                }
            case PrintJobInfo.STATE_STARTED:
                switch (after) {
                    case PrintJobInfo.STATE_QUEUED:
                    case PrintJobInfo.STATE_STARTED:
                        return false;
                    default:
                        return true;
                }
            case PrintJobInfo.STATE_BLOCKED:
                switch (after) {
                    case PrintJobInfo.STATE_STARTED:
                        // blocked -> started == restart
                    case PrintJobInfo.STATE_FAILED:
                    case PrintJobInfo.STATE_CANCELED:
                        return true;
                    default:
                        return false;
                }
            case PrintJobInfo.STATE_COMPLETED:
                return false;
            case PrintJobInfo.STATE_FAILED:
                return false;
            case PrintJobInfo.STATE_CANCELED:
                return false;
            default:
                // not reached
                throw new IllegalArgumentException("Cannot switch from " + before);
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

    @Parameterized.Parameters
    public static Collection<Object[]> getParameters() {
        ArrayList<Object[]> parameters = new ArrayList<>((int)Math.pow(STATES.length, 3));
        for (final int state1 : STATES) {
            for (final int state2 : STATES) {
                for (final int state3 : STATES) {
                    // No need to test the same non-transitions twice
                    if (state1 == state2 && state2 == state3) {
                        continue;
                    }

                    // QUEUED does not actually set a state, see setState
                    if (state1 == PrintJobInfo.STATE_QUEUED) {
                        continue;
                    }

                    parameters.add(new Object[]{state1, state2, state3});
                }
            }
        }

        return parameters;
    }

    @Before
    public void setPrinter() throws Exception {
        if (!sHasBeenSetUp) {
            createActivity();

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

    /**
     * Base test for the print job tests. Starts a print job and executes a testFn once the job is
     * queued.
     *
     * @throws Exception If anything is unexpected.
     */
    @Test
    @NoActivity
    public void stateTransitions() throws Exception {
        // No need to repeat what previously failed
        if(sKnownFailures[mState1][mState2] || sKnownFailures[mState2][mState3]) {
            Log.i(LOG_TAG, "Skipped " + mState1 + " -> " + mState2 + " -> " + mState3);
            return;
        } else {
            Log.i(LOG_TAG, "Test " + mState1 + " -> " + mState2 + " -> " + mState3);
            if (getActivity() == null) {
                createActivity();
            }
        }

        // Create the session of the printers that we will be checking.
        PrinterDiscoverySessionCallbacks sessionCallbacks
                = createFirstMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createFirstMockPrinterServiceCallbacks(
                sessionCallbacks, printJob -> {
                    sKnownFailures[PrintJobInfo.STATE_QUEUED][mState1] = true;

                    boolean success = setState(printJob, mState1);
                    assertEquals(isStateTransitionAllowed(PrintJobInfo.STATE_QUEUED,
                            mState1), success);
                    if (!success) {
                        return;
                    }
                    checkState(printJob, mState1);

                    sKnownFailures[PrintJobInfo.STATE_QUEUED][mState1] = false;

                    sKnownFailures[mState1][mState2] = true;

                    success = setState(printJob, mState2);
                    assertEquals(isStateTransitionAllowed(mState1, mState2), success);
                    if (!success) {
                        return;
                    }
                    checkState(printJob, mState2);

                    sKnownFailures[mState1][mState2] = false;

                    sKnownFailures[mState2][mState3] = true;

                    success = setState(printJob, mState3);
                    assertEquals(isStateTransitionAllowed(mState2, mState3), success);
                    if (!success) {
                        return;
                    }
                    checkState(printJob, mState3);

                    sKnownFailures[mState2][mState3] = false;
                });

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // We don't use the second service, but we have to still configure it
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

        // Start printing.
        print(adapter);

        // Wait until adapter is done
        waitForWriteAdapterCallback(1);

        for (int i = 0; i < 2; i++) {
            clickPrintButton();

            try {
                // Wait for print job to be queued
                waitForServiceOnPrintJobQueuedCallbackCalled(1);
                break;
            } catch (Throwable e) {
                if (i == 0) {
                    Log.i(LOG_TAG, "Print job was not queued, retrying", e);
                } else {
                    throw e;
                }
            }
        }

        // Wait for discovery session to be destroyed to isolate tests from each other
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        assertTrue(mTestSuccess[0]);
    }
}
