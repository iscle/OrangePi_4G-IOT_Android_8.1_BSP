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

package android.print.cts;

import static android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT;
import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.cts.services.FirstPrintService;
import android.print.cts.services.PrintServiceCallbacks;
import android.print.cts.services.PrinterDiscoverySessionCallbacks;
import android.print.cts.services.SecondPrintService;
import android.print.cts.services.StubbablePrinterDiscoverySession;
import android.print.pdf.PrintedPdfDocument;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.support.annotation.NonNull;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Adjust the pages to print and verify that the right pages were printed
 */
@RunWith(Parameterized.class)
public class PageRangeAdjustAndVerify extends BasePrintTest {
    private static final String LOG_TAG = PageRangeAdjustAndVerify.class.getSimpleName();

    private static final String FIRST_PRINTER = "First printer";

    private static boolean sIsDefaultPrinterSet;

    private final int[] mSelectedPages;
    private final int mNumPages;
    private final boolean mReportNumPagesFromLayout;
    private final boolean mWriteAllPages;

    @Before
    public void setDefaultPrinter() throws Exception {
        if (!sIsDefaultPrinterSet) {
            // Create a callback for the target print service.
            PrintServiceCallbacks firstServiceCallbacks = createMockPrintServiceCallbacks(
                    invocation -> createMockFirstPrinterDiscoverySessionCallbacks(),
                    invocation -> {
                        PrintJob printJob = (PrintJob) invocation.getArguments()[0];
                        printJob.complete();
                        return null;
                    }, null);

            // Configure the print services.
            FirstPrintService.setCallbacks(firstServiceCallbacks);
            SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

            // Create a mock print adapter.
            final PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

            makeDefaultPrinter(adapter, FIRST_PRINTER);
            resetCounters();

            sIsDefaultPrinterSet = true;
        }
    }

    @Parameterized.Parameters
    public static Collection<Object[]> getParameters() {
        ArrayList<Object[]> parameters = new ArrayList<>();

        for (int reportNumPagesFromLayout = 0; reportNumPagesFromLayout < 2;
                reportNumPagesFromLayout++) {
            for (int writeAllPages = 0; writeAllPages < 2; writeAllPages++) {
                for (int numPages = 5; numPages <= 100; numPages += 95) {
                    for (int firstGap = 0; firstGap < 3; firstGap++) {
                        for (int secondGap = 0; secondGap < 2; secondGap++) {
                            for (int thirdGap = 0; thirdGap < 2; thirdGap++) {
                                int[] selectedPages = new int[3];

                                if (firstGap == 0) {
                                    selectedPages[0] = 0;
                                } else if (firstGap == 1) {
                                    selectedPages[0] = 1;
                                } else {
                                    if (numPages == 5) {
                                        continue;
                                    } else {
                                        selectedPages[0] = 52;
                                    }
                                }

                                if (secondGap == 0) {
                                    selectedPages[1] = selectedPages[0] + 1;
                                } else {
                                    selectedPages[1] = selectedPages[0] + 2;
                                }

                                if (thirdGap == 0) {
                                    selectedPages[2] = selectedPages[1] + 1;
                                } else {
                                    selectedPages[2] = numPages - 1;
                                }

                                if (selectedPages[1] < selectedPages[2]
                                        && selectedPages[2] <= numPages - 1
                                        && (numPages == 5 || selectedPages[2] >= 50)) {
                                    parameters.add(
                                            new Object[]{numPages, reportNumPagesFromLayout != 0,
                                                    writeAllPages != 0, selectedPages});
                                }
                            }
                        }
                    }
                }
            }
        }

        return parameters;
    }

    public PageRangeAdjustAndVerify(int numPages, boolean reportNumPagesFromLayout,
            boolean writeAllPages, int[] selectedPages) {
        mNumPages = numPages;
        mReportNumPagesFromLayout = reportNumPagesFromLayout;
        mWriteAllPages = writeAllPages;
        mSelectedPages = selectedPages;
    }

    private boolean pageRangeContains(@NonNull PageRange[] pagesRanges, int pageNumber) {
        for (PageRange pageRange : pagesRanges) {
            if (pageRange.equals(PageRange.ALL_PAGES)
                    || pageRange.getStart() <= pageNumber && pageNumber <= pageRange.getEnd()) {
                return true;
            }
        }

        return false;
    }

    @Test
    public void adjustPageRangeAndVerifyPages() throws Throwable {
        Log.i(LOG_TAG, "mNumPages=" + mNumPages + " mReportNumPagesFromLayout="
                + mReportNumPagesFromLayout + " mWriteAllPages=" + mWriteAllPages
                + " mSelectedPages=" + Arrays.toString(mSelectedPages));

        // Create a callback for the target print service.
        PrintServiceCallbacks firstServiceCallbacks = createMockPrintServiceCallbacks(
                invocation -> createMockFirstPrinterDiscoverySessionCallbacks(),
                invocation -> {
                    PrintJob printJob = (PrintJob) invocation.getArguments()[0];

                    assertEquals(mSelectedPages.length,
                            printJob.getDocument().getInfo().getPageCount());
                    assertArrayEquals(new PageRange[]{PageRange.ALL_PAGES},
                            printJob.getInfo().getPages());

                    ParcelFileDescriptor original = printJob.getDocument().getData();

                    // Copy in file
                    File copy = File.createTempFile("tmp", ".pdf", getActivity().getFilesDir());
                    Log.i(LOG_TAG, "File is " + copy);

                    try (FileInputStream in = new FileInputStream(original.getFileDescriptor());
                         FileOutputStream out = new FileOutputStream(copy)) {
                        byte[] buffer = new byte[1024];
                        while (true) {
                            int numRead = in.read(buffer);

                            if (numRead == -1) {
                                break;
                            }

                            out.write(buffer, 0, numRead);
                        }
                    }
                    original.close();

                    printJob.start();

                    // Verify that the selected pages are colored correctly
                    try (PdfRenderer renderer = new PdfRenderer(ParcelFileDescriptor.open(copy,
                            MODE_READ_ONLY))) {
                        assertEquals(mSelectedPages.length, renderer.getPageCount());

                        Bitmap rendering = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);

                        for (int i = 0; i < 3; i++) {
                            try (PdfRenderer.Page page = renderer.openPage(i)) {
                                page.render(rendering, null, null, RENDER_MODE_FOR_PRINT);
                            }

                            int color;
                            switch (i) {
                                case 0:
                                    color = 0xffff0000;
                                    break;
                                case 1:
                                    color = 0xff00ff00;
                                    break;
                                case 2:
                                    color = 0xff0000ff;
                                    break;
                                default:
                                    color = 0xffffffff;
                                    break;
                            }

                            assertEquals(mSelectedPages[i] + "@" + i, color,
                                    rendering.getPixel(25, 25));
                        }

                        rendering.recycle();
                    }

                    printJob.complete();

                    onPrintJobQueuedCalled();
                    return null;
                }, null);

        final PrintAttributes[] printAttributes = new PrintAttributes[1];

        FirstPrintService.setCallbacks(firstServiceCallbacks);
        SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

        final PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    boolean changed = !Objects.equals(printAttributes[0],
                            invocation.getArguments()[1]);

                    printAttributes[0] = (PrintAttributes) invocation.getArguments()[1];

                    PrintDocumentInfo.Builder docBuilder =
                            new PrintDocumentInfo.Builder(PRINT_JOB_NAME);
                    if (mReportNumPagesFromLayout) {
                        docBuilder.setPageCount(mNumPages);
                    }

                    ((PrintDocumentAdapter.LayoutResultCallback) invocation.getArguments()[3])
                            .onLayoutFinished(
                                    docBuilder.build(), changed);

                    onLayoutCalled();
                    return null;
                }, invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] requestedPageRanges = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    PrintDocumentAdapter.WriteResultCallback
                            callback = (PrintDocumentAdapter.WriteResultCallback) args[3];

                    // Remember which pages to create
                    boolean[] pagesToPrint = new boolean[mNumPages];
                    for (int pageNumber = 0; pageNumber < mNumPages; pageNumber++) {
                        if (pageRangeContains(requestedPageRanges, pageNumber)) {
                            pagesToPrint[pageNumber] = true;
                        }
                    }

                    PrintedPdfDocument document = new PrintedPdfDocument(getActivity(),
                            printAttributes[0]);

                    Paint paint = new Paint();
                    paint.setStyle(Paint.Style.FILL);

                    // Create pages
                    ArrayList<PageRange> printedPages = new ArrayList<>();

                    for (int pageNumber = 0; pageNumber < mNumPages; pageNumber++) {
                        if (!mWriteAllPages && !pagesToPrint[pageNumber]) {
                            continue;
                        }

                        printedPages.add(new PageRange(pageNumber, pageNumber));

                        int color = 0xffffffff;
                        if (pageNumber == mSelectedPages[0]) {
                            color = 0xffff0000;
                        } else if (pageNumber == mSelectedPages[1]) {
                            color = 0xff00ff00;
                        } else if (pageNumber == mSelectedPages[2]) {
                            color = 0xff0000ff;
                        }

                        PdfDocument.Page page = document.startPage(pageNumber);

                        // Color selected pages
                        if (color != 0xffffffff) {
                            Canvas canvas = page.getCanvas();
                            paint.setColor(color);
                            canvas.drawRect(new Rect(0, 0, canvas.getWidth(), canvas.getHeight()),
                                    paint);
                        }

                        document.finishPage(page);
                    }

                    try (FileOutputStream fos = new FileOutputStream(fd.getFileDescriptor())) {
                        document.writeTo(fos);
                        fos.flush();
                        document.close();
                    }

                    fd.close();

                    callback.onWriteFinished(
                            printedPages.toArray(new PageRange[printedPages.size()]));

                    onWriteCalled();
                    return null;
                }, invocation -> {
                    onFinishCalled();
                    return null;
                });

        print(adapter);
        waitForWriteAdapterCallback(1);

        openPrintOptions();
        selectPages((mSelectedPages[0] + 1) + ", " + (mSelectedPages[1] + 1) + ", "
                + (mSelectedPages[2] + 1), mNumPages);

        clickPrintButton();
        waitForAdapterFinishCallbackCalled();
        waitForServiceOnPrintJobQueuedCallbackCalled(1);
    }

    private PrinterDiscoverySessionCallbacks createMockFirstPrinterDiscoverySessionCallbacks() {
        return createMockPrinterDiscoverySessionCallbacks(invocation -> {
            PrinterDiscoverySessionCallbacks mock = (PrinterDiscoverySessionCallbacks)
                    invocation.getMock();

            StubbablePrinterDiscoverySession session = mock.getSession();
            PrintService service = session.getService();

            if (session.getPrinters().isEmpty()) {
                List<PrinterInfo> printers = new ArrayList<>();

                // Add one printer.
                PrinterId firstPrinterId = service.generatePrinterId("first_printer");
                PrinterCapabilitiesInfo firstCapabilities =
                        new PrinterCapabilitiesInfo.Builder(firstPrinterId)
                                .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                                .addResolution(new PrintAttributes.Resolution("300x300", "300x300",
                                        300, 300), true)
                                .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                        PrintAttributes.COLOR_MODE_COLOR)
                                .build();
                PrinterInfo firstPrinter = new PrinterInfo.Builder(firstPrinterId,
                        FIRST_PRINTER, PrinterInfo.STATUS_IDLE)
                        .setCapabilities(firstCapabilities)
                        .build();
                printers.add(firstPrinter);

                session.addPrinters(printers);
            }

            return null;
        }, null, null, null, null, null, invocation -> {
                onPrinterDiscoverySessionDestroyCalled();
                return null;
            });
    }

    private PrintServiceCallbacks createSecondMockPrintServiceCallbacks() {
        return createMockPrintServiceCallbacks(null, null, null);
    }
}
