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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.os.Bundle;
import android.os.CancellationSignal;
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
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This test verifies that the system respects the {@link PrintDocumentAdapter}
 * contract and invokes all callbacks as expected.
 */
@RunWith(AndroidJUnit4.class)
public class PrintDocumentAdapterContractTest extends BasePrintTest {
    private static final String LOG_TAG = "PrintDocumentAdapterContractTest";

    @Before
    public void setDefaultPrinter() throws Exception {
        FirstPrintService.setCallbacks(createFirstMockPrintServiceCallbacks());
        SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());
    }

    @Before
    public void clearPrintSpoolerState() throws Exception {
        clearPrintSpoolerData();
    }

    @Test
    public void noPrintOptionsOrPrinterChange() throws Exception {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(2)
                            .build();
                    callback.onLayoutFinished(info, false);
                    // Mark layout was called.
                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];
                    writeBlankPages(printAttributes[0], fd, pages[0].getStart(), pages[0].getEnd());
                    fd.close();
                    callback.onWriteFinished(new PageRange[]{new PageRange(0, 1)});
                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Select the second printer.
        selectPrinter("Second printer");

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(2);

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // We always ask for the the first fifty pages for preview.
        PageRange[] firstPages = new PageRange[] {new PageRange(0, 1)};
        inOrder.verify(adapter).onWrite(eq(firstPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // We selected the second printer which does not support the media
        // size that was selected, so a new layout happens as the size changed.
        // Since we passed false to the layout callback meaning that the content
        // didn't change, there shouldn't be a next call to write.
        PrintAttributes secondOldAttributes = firstNewAttributes;
        PrintAttributes secondNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(MediaSize.ISO_A3)
                .setResolution(new Resolution("300x300", "300x300", 300, 300))
                .setMinMargins(Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_LONG_EDGE)
                .build();
        verifyLayoutCall(inOrder, adapter, secondOldAttributes, secondNewAttributes, true);

        // When print is pressed we ask for a layout which is *not* for preview.
        verifyLayoutCall(inOrder, adapter, secondNewAttributes, secondNewAttributes, false);

        // When print is pressed we ask for all selected pages but we got
        // them when asking for the ones for a preview, and the adapter does
        // not report a content change. Hence, there is nothing to write.

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void noPrintOptionsOrPrinterChangeCanceled() throws Exception {
        // Create a mock print adapter.
        PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Cancel the printing.
        getUiDevice().pressBack(); // wakes up the device.
        getUiDevice().pressBack();

        // Wait for finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // We always ask for the the first fifty pages for preview.
        PageRange[] firstPages = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(firstPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void nonCallingBackWrite() throws Throwable {
        final PrintAttributes[] lastLayoutPrintAttributes = new PrintAttributes[1];
        final boolean[] isWriteBroken = new boolean[1];

        // Create a mock print adapter.
        PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    PrintAttributes printAttributes =
                            (PrintAttributes) invocation.getArguments()[1];
                    PrintDocumentAdapter.LayoutResultCallback callback =
                            (PrintDocumentAdapter.LayoutResultCallback) invocation
                                    .getArguments()[3];

                    callback.onLayoutFinished(new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(),
                            lastLayoutPrintAttributes[0] != printAttributes);

                    lastLayoutPrintAttributes[0] = printAttributes;

                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PrintDocumentAdapter.WriteResultCallback callback =
                            (PrintDocumentAdapter.WriteResultCallback) args[3];

                    if (isWriteBroken[0]) {
                        ((CancellationSignal) args[2])
                                .setOnCancelListener(() -> callback.onWriteCancelled());
                    } else {
                        try (ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1]) {
                            writeBlankPages(lastLayoutPrintAttributes[0], fd, 0, 1);
                        }
                        callback.onWriteFinished(new PageRange[]{new PageRange(0, 0)});
                    }

                    onWriteCalled();
                    return null;
                }, invocation -> {
                    onFinishCalled();
                    return null;
                });

        // never return from writes until we repair the write call later
        isWriteBroken[0] = false;

        // Start printing.
        print(adapter);

        // Wait for write. This will happen as the the first layout always triggers a write
        waitForWriteAdapterCallback(1);

        // Make write broken
        isWriteBroken[0] = true;
        selectPrinter("Second printer");

        assertNoPrintButton();

        // Wait for write (The second printer causes a re-layout as it does not support the default
        // page size). The write is broken, hence this never returns
        waitForWriteAdapterCallback(2);

        assertNoPrintButton();

        getUiDevice().pressBack();
        getUiDevice().pressBack();

        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    @Test
    public void nonChangePrinterWhileNotWritten() throws Exception {
        final PrintAttributes[] lastLayoutPrintAttributes = new PrintAttributes[1];
        final boolean[] isWriteBroken = new boolean[1];

        // Create a mock print adapter.
        PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    PrintAttributes printAttributes =
                            (PrintAttributes) invocation.getArguments()[1];
                    PrintDocumentAdapter.LayoutResultCallback callback =
                            (PrintDocumentAdapter.LayoutResultCallback) invocation
                                    .getArguments()[3];

                    callback.onLayoutFinished(new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(),
                            lastLayoutPrintAttributes[0] != printAttributes);

                    lastLayoutPrintAttributes[0] = printAttributes;

                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PrintDocumentAdapter.WriteResultCallback callback =
                            (PrintDocumentAdapter.WriteResultCallback) args[3];

                    if (isWriteBroken[0]) {
                        ((CancellationSignal) args[2]).setOnCancelListener(() -> {
                            callback.onWriteCancelled();
                            onWriteCancelCalled();
                        });
                    } else {
                        try (ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1]) {
                            writeBlankPages(lastLayoutPrintAttributes[0], fd, 0, 1);
                        }
                        callback.onWriteFinished(new PageRange[]{new PageRange(0, 1)});
                    }

                    onWriteCalled();
                    return null;
                }, invocation -> {
                    onFinishCalled();
                    return null;
                });

        // Fake a very long write
        isWriteBroken[0] = true;

        // Start printing.
        print(adapter);

        // Wait for write. This will happen as the the first layout always triggers a write
        waitForWriteAdapterCallback(1);

        // Repair write and cause a re-layout by changing the printer
        isWriteBroken[0] = false;
        selectPrinter("Second printer");

        // The first write should be canceled and the second write succeeds
        waitForWriteCancelCallback(1);
        waitForWriteAdapterCallback(2);

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    @Test
    public void printOptionsChangeAndNoPrinterChange() throws Exception {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback = (LayoutResultCallback)
                            invocation.getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build();
                    callback.onLayoutFinished(info, false);
                    // Mark layout was called.
                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];
                    writeBlankPages(printAttributes[0], fd, pages[0].getStart(), pages[0].getEnd());
                    fd.close();
                    callback.onWriteFinished(pages);
                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Open the print options.
        openPrintOptions();

        // Select the second printer.
        selectPrinter("Second printer");

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(2);

        // Change the orientation.
        changeOrientation(getPrintSpoolerStringArray("orientation_labels")[1]);

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(3);

        // Change the media size.
        changeMediaSize(MediaSize.ISO_A0.getLabel(getActivity().getPackageManager()));

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(4);

        // Change the color.
        changeColor(getPrintSpoolerStringArray("color_mode_labels")[0]);

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(5);

        // Change the duplex.
        changeDuplex(getPrintSpoolerStringArray("duplex_mode_labels")[2]);

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(6);

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // We always ask for the the first fifty pages for preview.
        PageRange[] firstPages = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(firstPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // We selected the second printer which does not support the media
        // size that was selected, so a new layout happens as the size changed.
        // Since we passed false to the layout callback meaning that the content
        // didn't change, there shouldn't be a next call to write.
        PrintAttributes secondOldAttributes = firstNewAttributes;
        PrintAttributes secondNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(MediaSize.ISO_A3)
                .setResolution(new Resolution("300x300", "300x300", 300, 300))
                .setMinMargins(Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_LONG_EDGE)
                .build();
        verifyLayoutCall(inOrder, adapter, secondOldAttributes, secondNewAttributes, true);

        // We changed the orientation which triggers a layout. Since we passed
        // false to the layout callback meaning that the content didn't change,
        // there shouldn't be a next call to write.
        PrintAttributes thirdOldAttributes = secondNewAttributes;
        PrintAttributes thirdNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(MediaSize.ISO_A3.asLandscape())
                .setResolution(new Resolution("300x300", "300x300", 300, 300))
                .setMinMargins(Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_LONG_EDGE)
                .build();
        verifyLayoutCall(inOrder, adapter, thirdOldAttributes, thirdNewAttributes, true);

        // We changed the media size which triggers a layout. Since we passed
        // false to the layout callback meaning that the content didn't change,
        // there shouldn't be a next call to write.
        PrintAttributes fourthOldAttributes = thirdNewAttributes;
        PrintAttributes fourthNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(MediaSize.ISO_A0.asLandscape())
                .setResolution(new Resolution("300x300", "300x300", 300, 300))
                .setMinMargins(Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_LONG_EDGE)
                .build();
        verifyLayoutCall(inOrder, adapter, fourthOldAttributes, fourthNewAttributes, true);

        // We changed the color which triggers a layout. Since we passed
        // false to the layout callback meaning that the content didn't change,
        // there shouldn't be a next call to write.
        PrintAttributes fifthOldAttributes = fourthNewAttributes;
        PrintAttributes fifthNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(MediaSize.ISO_A0.asLandscape())
                .setResolution(new Resolution("300x300", "300x300", 300, 300))
                .setMinMargins(Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_LONG_EDGE)
                .build();
        verifyLayoutCall(inOrder, adapter, fifthOldAttributes, fifthNewAttributes, true);

        // We changed the duplex which triggers a layout. Since we passed
        // false to the layout callback meaning that the content didn't change,
        // there shouldn't be a next call to write.
        PrintAttributes sixthOldAttributes = fifthNewAttributes;
        PrintAttributes sixthNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(MediaSize.ISO_A0.asLandscape())
                .setResolution(new Resolution("300x300", "300x300", 300, 300))
                .setMinMargins(Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_SHORT_EDGE)
                .build();
        verifyLayoutCall(inOrder, adapter, sixthOldAttributes, sixthNewAttributes, true);

        // When print is pressed we ask for a layout which is *not* for preview.
        verifyLayoutCall(inOrder, adapter, sixthNewAttributes, sixthNewAttributes, false);

        // When print is pressed we ask for all selected pages but we got
        // them when asking for the ones for a preview, and the adapter does
        // not report a content change. Hence, there is nothing to write.

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    /* Disabled @Test, as this will intentionally kill the activity that started the test */
    public void printCorruptedFile() throws Exception {
        final boolean[] writeCorruptedFile = new boolean[1];
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback = (LayoutResultCallback)
                            invocation.getArguments()[3];
                    Bundle extras = (Bundle) invocation.getArguments()[4];
                    Log.i(LOG_TAG, "Preview: " + extras.getBoolean(
                            PrintDocumentAdapter.EXTRA_PRINT_PREVIEW));
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .build();
                    callback.onLayoutFinished(info, true);
                    // Mark layout was called.
                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];

                    if (writeCorruptedFile[0]) {
                        Log.i(LOG_TAG, "write corrupted file " + Arrays.toString(pages));

                        FileOutputStream os = new FileOutputStream(fd.getFileDescriptor());
                        for (int i = 0; i < 10; i++) {
                            os.write(i);
                        }
                    } else {
                        Log.i(LOG_TAG, "write good file");

                        writeBlankPages(printAttributes[0], fd, 0, 1);
                    }
                    fd.close();
                    callback.onWriteFinished(new PageRange[]{new PageRange(0, 1)});

                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Select the second printer.
        selectPrinter("First printer");

        openPrintOptions();
        selectPages("1", 2);

        // Wait for write.
        waitForWriteAdapterCallback(2);

        writeCorruptedFile[0] = true;

        // Click the print button
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Printing will abort automatically

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }


    @Test
    public void printOptionsChangeAndPrinterChange() throws Exception {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback = (LayoutResultCallback)
                            invocation.getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build();
                    callback.onLayoutFinished(info, false);
                    // Mark layout was called.
                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];
                    writeBlankPages(printAttributes[0], fd, pages[0].getStart(), pages[0].getEnd());
                    fd.close();
                    callback.onWriteFinished(pages);
                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Open the print options.
        openPrintOptions();

        // Select the second printer.
        selectPrinter("Second printer");

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(2);

        // Change the color.
        changeColor(getPrintSpoolerStringArray("color_mode_labels")[0]);

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(3);

        // Change the printer to one which supports the current media size.
        // Select the second printer.
        selectPrinter("First printer");

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(4);

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // We always ask for the the first fifty pages for preview.
        PageRange[] firstPages = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(firstPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // We changed the printer and the new printer does not support the
        // selected media size in which case the default media size of the
        // printer is used resulting in a layout pass. Same for margins.
        PrintAttributes secondOldAttributes = firstNewAttributes;
        PrintAttributes secondNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(MediaSize.ISO_A3)
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(new Margins(0, 0, 0, 0))
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_LONG_EDGE)
                .build();
        verifyLayoutCall(inOrder, adapter, secondOldAttributes, secondNewAttributes, true);

        // We changed the color which results in a layout pass.
        PrintAttributes thirdOldAttributes = secondNewAttributes;
        PrintAttributes thirdNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(MediaSize.ISO_A3)
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(new Margins(0, 0, 0, 0))
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_LONG_EDGE)
                .build();
        verifyLayoutCall(inOrder, adapter, thirdOldAttributes, thirdNewAttributes, true);

        // We changed the printer to one that does not support the current
        // media size in which case we pick the default media size for the
        // new printer which results in a layout pass. Same for color.
        PrintAttributes fourthOldAttributes = thirdNewAttributes;
        PrintAttributes fourthNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(MediaSize.ISO_A0)
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(new Margins(200, 200, 200, 200))
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, fourthOldAttributes, fourthNewAttributes, true);

        // When print is pressed we ask for a layout which is *not* for preview.
        verifyLayoutCall(inOrder, adapter, fourthNewAttributes, fourthNewAttributes, false);

        // When print is pressed we ask for all selected pages but we got
        // them when asking for the ones for a preview, and the adapter does
        // not report a content change. Hence, there is nothing to write.

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void printOptionsChangeAndNoPrinterChangeAndContentChange()
            throws Exception {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1)
                            .build();
                    // The content changes after every layout.
                    callback.onLayoutFinished(info, true);
                    // Mark layout was called.
                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];
                    writeBlankPages(printAttributes[0], fd, pages[0].getStart(), pages[0].getEnd());
                    fd.close();
                    callback.onWriteFinished(pages);
                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Open the print options.
        openPrintOptions();

        // Select the second printer.
        selectPrinter("Second printer");

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(2);

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // We always ask for the the first fifty pages for preview.
        PageRange[] firstPages = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(firstPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // We selected the second printer which does not support the media
        // size that was selected, so a new layout happens as the size changed.
        PrintAttributes secondOldAttributes = firstNewAttributes;
        PrintAttributes secondNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(MediaSize.ISO_A3)
                .setResolution(new Resolution("300x300", "300x300", 300, 300))
                .setMinMargins(Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_LONG_EDGE)
                .build();
        verifyLayoutCall(inOrder, adapter, secondOldAttributes, secondNewAttributes, true);

        // In the layout callback we reported that the content changed,
        // so the previously written page has to be written again.
        PageRange[] secondPages = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(secondPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // When print is pressed we ask for a layout which is *not* for preview.
        verifyLayoutCall(inOrder, adapter, secondNewAttributes, secondNewAttributes, false);

        // When print is pressed we ask for all selected pages as the adapter
        // reports that the content changes after every layout pass.
        PageRange[] thirdPages = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(thirdPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void newPrinterSupportsSelectedPrintOptions() throws Exception {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1)
                            .build();
                    // The content changes after every layout.
                    callback.onLayoutFinished(info, true);
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];
                    writeBlankPages(printAttributes[0], fd, pages[0].getStart(), pages[0].getEnd());
                    fd.close();
                    callback.onWriteFinished(pages);
                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Open the print options.
        openPrintOptions();

        // Select the third printer.
        selectPrinter("Third printer");

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // We always ask for the the first fifty pages for preview.
        PageRange[] firstPages = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(firstPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // When print is pressed we ask for a layout which is *not* for preview.
        verifyLayoutCall(inOrder, adapter, firstNewAttributes, firstNewAttributes, false);

        // When print is pressed we ask for all selected pages.
        PageRange[] thirdPages = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(thirdPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void nothingChangesAllPagesWrittenFirstTime() throws Exception {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(3)
                            .build();
                    callback.onLayoutFinished(info, false);
                    // Mark layout was called.
                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];
                    PageRange[] pages = (PageRange[]) args[0];
                    writeBlankPages(printAttributes[0], fd, pages[0].getStart(), pages[0].getEnd());
                    fd.close();
                    callback.onWriteFinished(new PageRange[] {PageRange.ALL_PAGES});
                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Open the print options.
        openPrintOptions();

        // Select the second printer.
        selectPrinter("Second printer");

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(2);

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // We always ask for the the first fifty pages for preview.
        PageRange[] firstPages = new PageRange[] {new PageRange(0, 2)};
        inOrder.verify(adapter).onWrite(eq(firstPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // We selected the second printer which does not support the media
        // size that was selected, so a new layout happens as the size changed.
        PrintAttributes secondOldAttributes = firstNewAttributes;
        PrintAttributes secondNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(MediaSize.ISO_A3)
                .setResolution(new Resolution("300x300", "300x300", 300, 300))
                .setMinMargins(Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_LONG_EDGE)
                .build();
        verifyLayoutCall(inOrder, adapter, secondOldAttributes, secondNewAttributes, true);

        // In the layout callback we reported that the content didn't change,
        // and we wrote all pages in the write call while being asked only
        // for the first page. Hence, all pages were written and they didn't
        // change, therefore no subsequent write call should happen.

        // When print is pressed we ask for a layout which is *not* for preview.
        verifyLayoutCall(inOrder, adapter, secondNewAttributes, secondNewAttributes, false);

        // In the layout callback we reported that the content didn't change,
        // and we wrote all pages in the write call while being asked only
        // for the first page. Hence, all pages were written and they didn't
        // change, therefore no subsequent write call should happen.

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void cancelLongRunningLayout() throws Exception {
        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    CancellationSignal cancellation = (CancellationSignal) invocation.getArguments()[2];
                    final LayoutResultCallback callback = (LayoutResultCallback) invocation
                            .getArguments()[3];
                    cancellation.setOnCancelListener(() -> {
                        onCancelOperationCalled();
                        callback.onLayoutCancelled();
                    });
                    onLayoutCalled();
                    return null;
                }, null, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(1);

        // Cancel printing.
        getUiDevice().pressBack(); // wakes up the device.
        getUiDevice().pressBack();

        // Wait for the cancellation request.
        waitForCancelOperationCallbackCalled();

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void cancelLongRunningWrite() throws Exception {
        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1)
                            .build();
                    callback.onLayoutFinished(info, false);
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    final ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    final CancellationSignal cancellation = (CancellationSignal) args[2];
                    final WriteResultCallback callback = (WriteResultCallback) args[3];
                    cancellation.setOnCancelListener(() -> {
                        try {
                            fd.close();
                        } catch (IOException ioe) {
                            /* ignore */
                        }
                        onCancelOperationCalled();
                        callback.onWriteCancelled();
                    });
                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Cancel printing.
        getUiDevice().pressBack(); // wakes up the device.
        getUiDevice().pressBack();

        // Wait for the cancellation request.
        waitForCancelOperationCallbackCalled();

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // We always ask for the first page for preview.
        PageRange[] firstPages = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(firstPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void failedLayout() throws Exception {
        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                    callback.onLayoutFailed(null);
                    // Mark layout was called.
                    onLayoutCalled();
                    return null;
                }, null, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(1);

        // Cancel printing.
        getUiDevice().pressBack(); // wakes up the device.
        getUiDevice().pressBack();

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // No write as layout failed.

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void failedWrite() throws Exception {
        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1)
                            .build();
                    callback.onLayoutFinished(info, false);
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];
                    fd.close();
                    callback.onWriteFailed(null);
                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Cancel printing.
        getUiDevice().pressBack(); // wakes up the device.
        getUiDevice().pressBack();

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // We always ask for the first page for preview.
        PageRange[] firstPages = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(firstPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void unexpectedLayoutCancel() throws Exception {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];
        final int[] numLayoutCalls = new int[1];

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];

                    // Returned cancelled for the second layout call which is unexpected
                    if (numLayoutCalls[0] == 1) {
                        callback.onLayoutCancelled();
                    } else {
                        callback.onLayoutFinished(
                                (new PrintDocumentInfo.Builder(PRINT_JOB_NAME)).build(), false);
                    }
                    numLayoutCalls[0]++;
                    // Mark layout was called.
                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];
                    writeBlankPages(printAttributes[0], fd, 0, 1);
                    fd.close();
                    callback.onWriteFinished(pages);
                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(1);

        // Select the second printer.
        selectPrinter("Second printer");

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(2);

        // Retry layout (which should succeed) as the layout call will stop canceling after the
        // second time
        clickRetryButton();

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    @Test
    public void unexpectedWriteCancel() throws Exception {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];
        final int[] numWriteCalls = new int[1];

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];

                    callback.onLayoutFinished(
                                (new PrintDocumentInfo.Builder(PRINT_JOB_NAME)).build(), true);

                    // Mark layout was called.
                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    WriteResultCallback callback = (WriteResultCallback) args[3];

                    // Returned cancelled for the second write call which is unexpected
                    if (numWriteCalls[0] == 1) {
                        callback.onWriteCancelled();
                    } else {
                        PageRange[] pages = (PageRange[]) args[0];
                        ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                        writeBlankPages(printAttributes[0], fd, 0, 1);
                        fd.close();
                        callback.onWriteFinished(pages);
                    }
                    numWriteCalls[0]++;

                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        waitForWriteAdapterCallback(1);

        // Select the second printer.
        selectPrinter("Second printer");

        // Wait for layout.
        waitForWriteAdapterCallback(2);

        // Retry write (which should succeed) as the write call will stop canceling after the
        // second time
        clickRetryButton();

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    @Test
    public void requestedPagesNotWritten() throws Exception {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                          .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1)
                          .build();
                    callback.onLayoutFinished(info, false);
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];
                    writeBlankPages(printAttributes[0], fd, Integer.MAX_VALUE, Integer.MAX_VALUE);
                    fd.close();
                    // Write wrong pages.
                    callback.onWriteFinished(new PageRange[] {
                            new PageRange(Integer.MAX_VALUE,Integer.MAX_VALUE)});
                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Cancel printing.
        getUiDevice().pressBack(); // wakes up the device.
        getUiDevice().pressBack();

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // We always ask for the first page for preview.
        PageRange[] firstPages = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(firstPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void layoutCallbackNotCalled() throws Exception {
        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    // Break the contract and never call the callback.
                    // Mark layout called.
                    onLayoutCalled();
                    return null;
                }, null, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(1);

        // Cancel printing.
        getUiDevice().pressBack(); // wakes up the device.
        getUiDevice().pressBack();

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void writeCallbackNotCalled() throws Exception {
        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    LayoutResultCallback callback = (LayoutResultCallback) invocation.getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(1)
                            .build();
                    callback.onLayoutFinished(info, false);
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    fd.close();
                    // Break the contract and never call the callback.
                    // Mark write was called.
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for write.
        waitForWriteAdapterCallback(1);

        // Cancel printing.
        getUiDevice().pressBack(); // wakes up the device.
        getUiDevice().pressBack();

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        // We always ask for the first page for preview.
        PageRange[] firstPages = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(firstPages), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void layoutCallbackCalledTwice() throws Exception {
        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1)
                            .build();
                    LayoutResultCallback callback = (LayoutResultCallback) invocation
                            .getArguments()[3];

                    // Break the contract and call the callback twice.
                    callback.onLayoutFinished(info, true);
                    callback.onLayoutFinished(info, true);

                    // Mark layout called.
                    onLayoutCalled();
                    return null;
                }, null, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(1);

        // Cancel printing.
        getUiDevice().pressBack(); // wakes up the device.
        getUiDevice().pressBack();

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        PageRange[] firstPage = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(firstPage), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void writeCallbackCalledTwice() throws Exception {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        // Create a mock print adapter.
        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(1)
                            .build();
                    LayoutResultCallback callback = (LayoutResultCallback) invocation
                            .getArguments()[3];

                    callback.onLayoutFinished(info, true);

                    // Mark layout called.
                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];

                    // Write only one pages
                    writeBlankPages(printAttributes[0], fd, 0, 0);
                    fd.close();

                    // Break the contract and call callback twice
                    callback.onWriteFinished(pages);
                    callback.onWriteFinished(pages);

                    // Mark write called
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    // Mark finish was called.
                    onFinishCalled();
                    return null;
                });

        // Start printing.
        print(adapter);

        // Wait for layout.
        waitForLayoutAdapterCallbackCount(1);

        // Cancel printing.
        getUiDevice().pressBack(); // wakes up the device.
        getUiDevice().pressBack();

        // Wait for a finish.
        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // Verify the expected calls.
        InOrder inOrder = inOrder(adapter);

        // Start is always called first.
        inOrder.verify(adapter).onStart();

        // Start is always followed by a layout. The PDF printer is selected if
        // there are other printers but none of them was used.
        PrintAttributes firstOldAttributes = new PrintAttributes.Builder().build();
        PrintAttributes firstNewAttributes = new PrintAttributes.Builder()
                .setMediaSize(getDefaultMediaSize())
                .setResolution(new Resolution("PDF resolution", "PDF resolution", 300, 300))
                .setMinMargins(Margins.NO_MARGINS).setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
        verifyLayoutCall(inOrder, adapter, firstOldAttributes, firstNewAttributes, true);

        PageRange[] firstPage = new PageRange[] {new PageRange(0, 0)};
        inOrder.verify(adapter).onWrite(eq(firstPage), any(ParcelFileDescriptor.class),
                any(CancellationSignal.class), any(WriteResultCallback.class));

        // Finish is always called last.
        inOrder.verify(adapter).onFinish();

        // No other call are expected.
        verifyNoMoreInteractions(adapter);
    }

    /**
     * Pretend to have written two pages, but only actually write one page
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void notEnoughPages() throws Exception {
        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback = (LayoutResultCallback) invocation
                            .getArguments()[3];

                    // Lay out two pages
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(2)
                            .build();
                    callback.onLayoutFinished(info, true);
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];

                    // Write only one pages
                    writeBlankPages(printAttributes[0], fd, 0, 0);
                    fd.close();

                    // Break the contract and report that two pages were written
                    callback.onWriteFinished(pages);
                    onWriteCalled();
                    return null;
                }, invocation -> {
                    onFinishCalled();
                    return null;
                });

        print(adapter);
        waitForWriteAdapterCallback(1);
        selectPrinter("First printer");
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        waitForAdapterFinishCallbackCalled();

        // Wait for the session to be destroyed to isolate tests.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
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

                // Add the second printer.
                PrinterId secondPrinterId = service.generatePrinterId("second_printer");
                PrinterCapabilitiesInfo secondCapabilities =
                        new PrinterCapabilitiesInfo.Builder(secondPrinterId)
                                .addMediaSize(MediaSize.ISO_A3, true)
                                .addMediaSize(MediaSize.ISO_A0, false)
                                .addResolution(new Resolution("200x200", "200x200", 200, 200), true)
                                .addResolution(new Resolution("300x300", "300x300", 300, 300),
                                        false)
                                .setColorModes(PrintAttributes.COLOR_MODE_COLOR
                                                | PrintAttributes.COLOR_MODE_MONOCHROME,
                                        PrintAttributes.COLOR_MODE_MONOCHROME
                                )
                                .setDuplexModes(PrintAttributes.DUPLEX_MODE_LONG_EDGE
                                                | PrintAttributes.DUPLEX_MODE_SHORT_EDGE,
                                        PrintAttributes.DUPLEX_MODE_LONG_EDGE
                                )
                                .build();
                PrinterInfo secondPrinter = new PrinterInfo.Builder(secondPrinterId,
                        "Second printer", PrinterInfo.STATUS_IDLE)
                        .setCapabilities(secondCapabilities)
                        .build();
                printers.add(secondPrinter);

                // Add the third printer.
                PrinterId thirdPrinterId = service.generatePrinterId("third_printer");
                PrinterCapabilitiesInfo thirdCapabilities =
                        null;
                try {
                    thirdCapabilities = new PrinterCapabilitiesInfo.Builder(thirdPrinterId)
                            .addMediaSize(getDefaultMediaSize(), true)
                            .addResolution(new Resolution("300x300", "300x300", 300, 300), true)
                            .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                    PrintAttributes.COLOR_MODE_COLOR)
                            .build();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Cannot create third printer", e);
                }
                PrinterInfo thirdPrinter = new PrinterInfo.Builder(thirdPrinterId,
                        "Third printer", PrinterInfo.STATUS_IDLE)
                        .setCapabilities(thirdCapabilities)
                        .build();
                printers.add(thirdPrinter);

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
