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

import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintAttributes.Margins;
import android.print.PrintAttributes.MediaSize;
import android.print.PrintAttributes.Resolution;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentAdapter.LayoutResultCallback;
import android.print.PrintDocumentAdapter.WriteResultCallback;
import android.print.PrintDocumentInfo;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.cts.services.FirstPrintService;
import android.print.cts.services.PrintServiceCallbacks;
import android.print.cts.services.PrinterDiscoverySessionCallbacks;
import android.print.cts.services.SecondPrintService;
import android.print.cts.services.StubbablePrinterDiscoverySession;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;
import android.util.Log;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static android.print.cts.Utils.runOnMainThread;

/**
 * This test verifies changes to the printer capabilities are applied correctly.
 */
@RunWith(Parameterized.class)
public class PrinterCapabilitiesChangeTest extends BasePrintTest {
    private static final String LOG_TAG = "PrinterCapChangeTest";
    private static final String PRINTER_NAME = "Test printer";

    private static final Margins DEFAULT_MARGINS = new Margins(0, 0, 0, 0);
    private static final Resolution RESOLUTION_300 =
            new Resolution("300", "300", 300, 300);

    private boolean mAvailBefore;
    private MediaSize mMsBefore;
    private boolean mAvailAfter;
    private MediaSize mMsAfter;

    private final StubbablePrinterDiscoverySession[] mSession = new StubbablePrinterDiscoverySession[1];
    private final PrinterId[] mPrinterId = new PrinterId[1];
    private final PrintAttributes[] mLayoutAttributes = new PrintAttributes[1];
    private final PrintAttributes[] mWriteAttributes = new PrintAttributes[1];
    private PrintDocumentAdapter mAdapter;
    private static boolean sHasDefaultPrinterBeenSet;

    /**
     * Generate a new list of printers containing a singer printer with the given media size and
     * status. The other capabilities are default values.
     *
     * @param printerId The id of the printer
     * @param mediaSize The media size to use
     * @param status    The status of th printer
     *
     * @return The list of printers
     */
    private List<PrinterInfo> generatePrinters(PrinterId printerId, MediaSize mediaSize,
            int status) {
        List<PrinterInfo> printers = new ArrayList<>(1);

        PrinterCapabilitiesInfo cap;

        if (mediaSize != null) {
            PrinterCapabilitiesInfo.Builder builder = new PrinterCapabilitiesInfo.Builder(
                    printerId);
            builder.setMinMargins(DEFAULT_MARGINS)
                    .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                            PrintAttributes.COLOR_MODE_COLOR)
                    .setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE,
                            PrintAttributes.DUPLEX_MODE_NONE)
                    .addMediaSize(mediaSize, true)
                    .addResolution(RESOLUTION_300, true);
            cap = builder.build();
        } else {
            cap = null;
        }

        printers.add(new PrinterInfo.Builder(printerId, PRINTER_NAME, status).setCapabilities(cap)
                .build());

        return printers;
    }

    /**
     * Wait until the print activity requested an update with print attributes matching the media
     * size.
     *
     * @param printAttributes The print attributes container
     * @param mediaSize       The media size to match
     *
     * @throws Exception If anything unexpected happened, e.g. the attributes did not change.
     */
    private void waitForMediaSizeChange(PrintAttributes[] printAttributes, MediaSize mediaSize)
            throws Exception {
        synchronized (PrinterCapabilitiesChangeTest.this) {
            long endTime = System.currentTimeMillis() + OPERATION_TIMEOUT_MILLIS;
            while (printAttributes[0] == null ||
                    !printAttributes[0].getMediaSize().equals(mediaSize)) {
                wait(Math.max(0, endTime - System.currentTimeMillis()));

                if (endTime < System.currentTimeMillis()) {
                    throw new TimeoutException(
                            "Print attributes did not change to " + mediaSize + " in " +
                                    OPERATION_TIMEOUT_MILLIS + " ms. Current attributes"
                                    + printAttributes[0]);
                }
            }
        }
    }

    /**
     * Change the media size of the capabilities of the printer
     *
     * @param session     The mSession used in the test
     * @param printerId   The printer to change
     * @param mediaSize   The new mediaSize to apply
     * @param isAvailable If the printer should be available or not
     */
    private void changeCapabilities(final StubbablePrinterDiscoverySession session,
            final PrinterId printerId, final MediaSize mediaSize, final boolean isAvailable)
            throws Throwable {
        runOnMainThread(
                () -> session.addPrinters(generatePrinters(printerId, mediaSize, isAvailable ?
                        PrinterInfo.STATUS_IDLE :
                        PrinterInfo.STATUS_UNAVAILABLE)));
    }

    /**
     * Wait until the message is shown that indicates that a printer is unavilable.
     *
     * @throws Exception If anything was unexpected.
     */
    private void waitForPrinterUnavailable() throws Exception {
        final String PRINTER_UNAVAILABLE_MSG =
                getPrintSpoolerString("print_error_printer_unavailable");

        UiObject message = getUiDevice().findObject(new UiSelector().resourceId(
                "com.android.printspooler:id/message"));
        if (!message.getText().equals(PRINTER_UNAVAILABLE_MSG)) {
            throw new Exception("Wrong message: " + message.getText() + " instead of " +
                    PRINTER_UNAVAILABLE_MSG);
        }
    }

    @Before
    public void setUpPrinting() throws Exception {
        // Create the mSession[0] callbacks that we will be checking.
        final PrinterDiscoverySessionCallbacks firstSessionCallbacks =
                createMockPrinterDiscoverySessionCallbacks(invocation -> {
                    mSession[0] = ((PrinterDiscoverySessionCallbacks) invocation.getMock())
                            .getSession();

                    mPrinterId[0] = mSession[0].getService().generatePrinterId(PRINTER_NAME);

                    mSession[0].addPrinters(generatePrinters(mPrinterId[0], MediaSize.NA_LETTER,
                            PrinterInfo.STATUS_IDLE));
                    return null;
                }, null, null, null, null, null, invocation -> {
                    onPrinterDiscoverySessionDestroyCalled();
                    return null;
                });

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks firstServiceCallbacks = createMockPrintServiceCallbacks(
                invocation -> firstSessionCallbacks, null, null);

        // Configure the print services.
        FirstPrintService.setCallbacks(firstServiceCallbacks);
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        mAdapter = createMockPrintDocumentAdapter(
                invocation -> {
                    LayoutResultCallback callback = (LayoutResultCallback) invocation
                            .getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1)
                            .build();

                    synchronized (PrinterCapabilitiesChangeTest.this) {
                        mLayoutAttributes[0] = (PrintAttributes) invocation.getArguments()[1];

                        PrinterCapabilitiesChangeTest.this.notify();
                    }

                    callback.onLayoutFinished(info, true);
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];

                    writeBlankPages(mLayoutAttributes[0], fd, pages[0].getStart(),
                            pages[0].getEnd());
                    fd.close();

                    synchronized (PrinterCapabilitiesChangeTest.this) {
                        mWriteAttributes[0] = mLayoutAttributes[0];

                        PrinterCapabilitiesChangeTest.this.notify();
                    }

                    callback.onWriteFinished(pages);

                    onWriteCalled();
                    return null;
                }, null);

        if (!sHasDefaultPrinterBeenSet) {
            makeDefaultPrinter(mAdapter, PRINTER_NAME);
            sHasDefaultPrinterBeenSet = true;
        }

        resetCounters();
    }

    @Test
    public void changeCapabilities() throws Throwable {
        Log.i(LOG_TAG, "Test case: " + mAvailBefore + ", " + mMsBefore + " -> " + mAvailAfter + ", "
                + mMsAfter);

        // Start printing.
        print(mAdapter);

        waitForWriteAdapterCallback(1);

        changeCapabilities(mSession[0], mPrinterId[0], mMsBefore, mAvailBefore);
        if (mAvailBefore && mMsBefore != null) {
            waitForMediaSizeChange(mLayoutAttributes, mMsBefore);
            waitForMediaSizeChange(mWriteAttributes, mMsBefore);
        } else {
            waitForPrinterUnavailable();
        }

        changeCapabilities(mSession[0], mPrinterId[0], mMsAfter, mAvailAfter);
        if (mAvailAfter && mMsAfter != null) {
            waitForMediaSizeChange(mLayoutAttributes, mMsAfter);
            waitForMediaSizeChange(mWriteAttributes, mMsAfter);
        } else {
            waitForPrinterUnavailable();
        }

        // Reset printer to default in case discovery mSession is reused
        changeCapabilities(mSession[0], mPrinterId[0], MediaSize.NA_LETTER, true);
        waitForMediaSizeChange(mLayoutAttributes, MediaSize.NA_LETTER);
        waitForMediaSizeChange(mWriteAttributes, MediaSize.NA_LETTER);

        getUiDevice().pressBack();

        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> getParameters() {
        ArrayList<Object[]> parameters = new ArrayList<>();

        parameters.add(new Object[]{false, null, false, null});
        parameters.add(new Object[]{false, null, false, MediaSize.ISO_A0});
        parameters.add(new Object[]{false, null, false, MediaSize.ISO_B0});
        parameters.add(new Object[]{false, null, true, null});
        parameters.add(new Object[]{false, null, true, MediaSize.ISO_A0});
        parameters.add(new Object[]{false, null, true, MediaSize.ISO_B0});
        parameters.add(new Object[]{false, MediaSize.ISO_A0, false, null});
        parameters.add(new Object[]{false, MediaSize.ISO_A0, false, MediaSize.ISO_A0});
        parameters.add(new Object[]{false, MediaSize.ISO_A0, false, MediaSize.ISO_B0});
        parameters.add(new Object[]{false, MediaSize.ISO_A0, true, null});
        parameters.add(new Object[]{false, MediaSize.ISO_A0, true, MediaSize.ISO_A0});
        parameters.add(new Object[]{false, MediaSize.ISO_A0, true, MediaSize.ISO_B0});
        parameters.add(new Object[]{true, null, false, null});
        parameters.add(new Object[]{true, null, false, MediaSize.ISO_B0});
        parameters.add(new Object[]{true, null, true, null});
        parameters.add(new Object[]{true, null, true, MediaSize.ISO_B0});
        parameters.add(new Object[]{true, MediaSize.ISO_A0, false, null});
        parameters.add(new Object[]{true, MediaSize.ISO_A0, false, MediaSize.ISO_A0});
        parameters.add(new Object[]{true, MediaSize.ISO_A0, false, MediaSize.ISO_B0});
        parameters.add(new Object[]{true, MediaSize.ISO_A0, true, null});
        parameters.add(new Object[]{true, MediaSize.ISO_A0, true, MediaSize.ISO_A0});
        parameters.add(new Object[]{true, MediaSize.ISO_A0, true, MediaSize.ISO_B0});

        return parameters;
    }

    public PrinterCapabilitiesChangeTest(boolean availBefore, MediaSize msBefore,
            boolean availAfter, MediaSize msAfter) {
        mAvailBefore = availBefore;
        mMsBefore = msBefore;
        mAvailAfter = availAfter;
        mMsAfter = msAfter;
    }
}
