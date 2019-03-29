/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import static android.print.cts.Utils.eventually;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This test verifies that the system respects the {@link PrintDocumentAdapter}
 * contract and invokes all callbacks as expected.
 */
@RunWith(AndroidJUnit4.class)
public class PrintDocumentInfoTest extends android.print.cts.BasePrintTest {
    private static boolean sIsDefaultPrinterSet;

    @Before
    public void setDefaultPrinter() throws Exception {
        if (!sIsDefaultPrinterSet) {
            // Create a callback for the target print service.
            FirstPrintService.setCallbacks(createFirstMockPrintServiceCallbacks());
            SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

            // Create a mock print adapter.
            final PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

            makeDefaultPrinter(adapter, "First printer");
            resetCounters();

            sIsDefaultPrinterSet = true;
        }
    }

    /**
     * Executes a print process with a given print document info
     *
     * @param name The name of the document info
     * @param contentType The content type of the document
     * @param pageCount The number of pages in the document
     */
    private void printDocumentBaseTest(String name, Integer contentType, Integer pageCount)
            throws Throwable {
        FirstPrintService.setCallbacks(createFirstMockPrintServiceCallbacks());
        SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

        PrintDocumentInfo.Builder b = new PrintDocumentInfo.Builder(name);
        if (contentType != null) {
            b.setContentType(contentType);
        }
        if (pageCount != null) {
            b.setPageCount(pageCount);
        }
        PrintDocumentInfo info = b.build();

        PrintDocumentInfo queuedInfo[] = new PrintDocumentInfo[1];
        ParcelFileDescriptor queuedData[] = new ParcelFileDescriptor[1];

        PrinterDiscoverySessionCallbacks printerDiscoverySessionCallbacks =
                createFirstMockDiscoverySessionCallbacks();
        PrintServiceCallbacks printServiceCallbacks = createMockPrintServiceCallbacks(
                invocation -> printerDiscoverySessionCallbacks,
                invocation -> {
                    PrintJob printJob = (PrintJob) invocation.getArguments()[0];
                    queuedInfo[0] = printJob.getDocument().getInfo();
                    queuedData[0] = printJob.getDocument().getData();
                    printJob.complete();
                    return null;
                }, null);

        FirstPrintService.setCallbacks(printServiceCallbacks);

        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback = (LayoutResultCallback) invocation
                            .getArguments()[3];
                    callback.onLayoutFinished(info, false);
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];
                    writeBlankPages(printAttributes[0], fd, 0, 1);
                    fd.close();
                    callback.onWriteFinished(pages);
                    onWriteCalled();
                    return null;
                }, invocation -> null);

        // Start printing.
        print(adapter);

        // Wait for layout.
        waitForWriteAdapterCallback(1);

        // Click the print button.
        clickPrintButton();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Check that the document name was carried over 1:1
        eventually(() -> assertEquals(name, queuedInfo[0].getName()));

        // Content type is set to document by default, but is otherwise unrestricted
        if (contentType != null) {
            assertEquals(contentType, Integer.valueOf(queuedInfo[0].getContentType()));
        } else {
            assertEquals(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT, queuedInfo[0].getContentType());
        }

        // Page count is set to the real value if unknown, 0 or unset.
        // Otherwise the set value is used
        if (pageCount != null && pageCount != PrintDocumentInfo.PAGE_COUNT_UNKNOWN
                && pageCount != 0) {
            assertEquals(pageCount, Integer.valueOf(queuedInfo[0].getPageCount()));
        } else {
            assertEquals(2, queuedInfo[0].getPageCount());
        }

        // Verify data (== pdf file) size
        assertTrue(queuedInfo[0].getDataSize() > 0);

        long bytesRead = 0;
        try (FileInputStream is = new FileInputStream(queuedData[0].getFileDescriptor())) {
            while (true) {
                int ret = is.read();
                if (ret == -1) {
                    break;
                }
                bytesRead++;
            }
        }
        assertEquals(queuedInfo[0].getDataSize(), bytesRead);
    }

    /**
     * Test that the default values of the PrintDocumentInfo are fine.
     *
     * @throws Exception If anything unexpected happens
     */
    @Test
    public void documentInfoNothingSet() throws Throwable {
        printDocumentBaseTest(PRINT_JOB_NAME, null, null);
    }

    /**
     * Test that a unknown page count is handled correctly.
     *
     * @throws Exception If anything unexpected happens
     */
    @Test
    public void documentInfoUnknownPageCount() throws Throwable {
        printDocumentBaseTest(PRINT_JOB_NAME, null, PrintDocumentInfo.PAGE_COUNT_UNKNOWN);
    }

    /**
     * Test that zero page count is handled correctly.
     *
     * @throws Exception If anything unexpected happens
     */
    @Test
    public void documentInfoZeroPageCount() throws Throwable {
        printDocumentBaseTest(PRINT_JOB_NAME, null, 0);
    }

    /**
     * Test that page count one is handled correctly. (The document has two pages)
     *
     * @throws Exception If anything unexpected happens
     */
    @Test
    public void documentInfoOnePageCount() throws Throwable {
        printDocumentBaseTest(PRINT_JOB_NAME, null, 1);
    }

    /**
     * Test that page count three is handled correctly. (The document has two pages)
     *
     * @throws Exception If anything unexpected happens
     */
    @Test
    public void documentInfoThreePageCount() throws Throwable {
        printDocumentBaseTest(PRINT_JOB_NAME, null, 3);
    }

    /**
     * Test that a photo content type is handled correctly.
     *
     * @throws Exception If anything unexpected happens
     */
    @Test
    public void documentInfoContentTypePhoto() throws Throwable {
        printDocumentBaseTest(PRINT_JOB_NAME, PrintDocumentInfo.CONTENT_TYPE_PHOTO, null);
    }

    /**
     * Test that a unknown content type is handled correctly.
     *
     * @throws Exception If anything unexpected happens
     */
    @Test
    public void documentInfoContentTypeUnknown() throws Throwable {
        printDocumentBaseTest(PRINT_JOB_NAME, PrintDocumentInfo.CONTENT_TYPE_UNKNOWN, null);
    }

    /**
     * Test that a undefined content type is handled correctly.
     *
     * @throws Exception If anything unexpected happens
     */
    @Test
    public void documentInfoContentTypeNonDefined() throws Throwable {
        printDocumentBaseTest(PRINT_JOB_NAME, -23, null);
    }

    private PrinterDiscoverySessionCallbacks createFirstMockDiscoverySessionCallbacks() {
        return createMockPrinterDiscoverySessionCallbacks(invocation -> {
            PrinterDiscoverySessionCallbacks mock = (PrinterDiscoverySessionCallbacks)
                    invocation.getMock();

            StubbablePrinterDiscoverySession session = mock.getSession();
            PrintService service = session.getService();

            if (session.getPrinters().isEmpty()) {
                List<PrinterInfo> printers = new ArrayList<>();

                // Add the first printer.
                PrinterId firstPrinterId = service.generatePrinterId("first_printer");
                PrinterCapabilitiesInfo firstCapabilities =
                        new PrinterCapabilitiesInfo.Builder(firstPrinterId)
                                .setMinMargins(new Margins(200, 200, 200, 200))
                                .addMediaSize(MediaSize.ISO_A0, true)
                                .addMediaSize(MediaSize.ISO_A5, false)
                                .addResolution(new Resolution("300x300", "300x300", 300, 300), true)
                                .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                        PrintAttributes.COLOR_MODE_COLOR)
                                .build();
                PrinterInfo firstPrinter = new PrinterInfo.Builder(firstPrinterId,
                        "First printer", PrinterInfo.STATUS_IDLE)
                        .setCapabilities(firstCapabilities)
                        .build();
                printers.add(firstPrinter);

                session.addPrinters(printers);
            }
            return null;
        }, null, null, null, null, null, invocation -> {
            // Take a note onDestroy was called.
            onPrinterDiscoverySessionDestroyCalled();
            return null;
        });
    }

    private PrintServiceCallbacks createFirstMockPrintServiceCallbacks() {
        final PrinterDiscoverySessionCallbacks callbacks =
                createFirstMockDiscoverySessionCallbacks();
        return createMockPrintServiceCallbacks(invocation -> callbacks, invocation -> {
            PrintJob printJob = (PrintJob) invocation.getArguments()[0];
            printJob.complete();
            return null;
        }, null);
    }

    private PrintServiceCallbacks createSecondMockPrintServiceCallbacks() {
        return createMockPrintServiceCallbacks(null, null, null);
    }
}
