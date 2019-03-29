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
import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

import static android.print.cts.Utils.assertException;
import static org.junit.Assert.*;

/**
 * This test verifies changes to the printer capabilities are applied correctly.
 */
@RunWith(AndroidJUnit4.class)
public class PrinterCapabilitiesTest extends BasePrintTest {
    private static final String PRINTER_NAME = "Test printer";

    private static final Margins DEFAULT_MARGINS = new Margins(0, 0, 0, 0);
    private static final PrintAttributes.Resolution RESOLUTION_300 =
            new PrintAttributes.Resolution("300", "300", 300, 300);
    private static final PrintAttributes.Resolution RESOLUTION_600 =
            new PrintAttributes.Resolution("600", "600", 600, 600);
    private static boolean sDefaultPrinterBeenSet;

    /**
     * That that you cannot create illegal PrinterCapabilityInfos.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void illegalPrinterCapabilityInfos() throws Exception {
        final PrinterDiscoverySessionCallbacks firstSessionCallbacks =
                createMockPrinterDiscoverySessionCallbacks(invocation -> {
                    StubbablePrinterDiscoverySession session =
                            ((PrinterDiscoverySessionCallbacks)
                                    invocation.getMock()).getSession();

                    PrinterId printerId = session.getService().generatePrinterId(PRINTER_NAME);

                    // printerId need to be set
                    assertException(() -> new PrinterCapabilitiesInfo.Builder(null),
                            IllegalArgumentException.class);

                    // All capability fields (beside duplex) need to be initialized:
                    // Test no color
                    assertException(() ->
                                    (new PrinterCapabilitiesInfo.Builder(printerId))
                                            .setMinMargins(DEFAULT_MARGINS)
                                            .addMediaSize(MediaSize.ISO_A4, true)
                                            .addResolution(RESOLUTION_300, true).build(),
                            IllegalStateException.class);
                    // Test bad colors
                    assertException(() ->
                                    (new PrinterCapabilitiesInfo.Builder(printerId))
                                            .setColorModes(0xffff,
                                                    PrintAttributes.COLOR_MODE_MONOCHROME),
                            IllegalArgumentException.class);
                    // Test bad duplex mode
                    assertException(() ->
                                    (new PrinterCapabilitiesInfo.Builder(printerId))
                                            .setDuplexModes(0xffff,
                                                    PrintAttributes.DUPLEX_MODE_NONE),
                            IllegalArgumentException.class);
                    // Test no mediasize
                    assertException(() ->
                                    (new PrinterCapabilitiesInfo.Builder(printerId))
                                            .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                                    PrintAttributes.COLOR_MODE_COLOR)
                                            .setMinMargins(DEFAULT_MARGINS)
                                            .addResolution(RESOLUTION_300, true).build(),
                            IllegalStateException.class);
                    // Test no default mediasize
                    assertException(() ->
                                    (new PrinterCapabilitiesInfo.Builder(printerId))
                                            .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                                    PrintAttributes.COLOR_MODE_COLOR)
                                            .setMinMargins(DEFAULT_MARGINS)
                                            .addMediaSize(MediaSize.ISO_A4, false)
                                            .addResolution(RESOLUTION_300, true).build(),
                            IllegalStateException.class);
                    // Test two default mediasizes
                    assertException(() ->
                                    (new PrinterCapabilitiesInfo.Builder(printerId))
                                            .addMediaSize(MediaSize.ISO_A4, true)
                                            .addMediaSize(MediaSize.ISO_A5, true),
                            IllegalArgumentException.class);
                    // Test no resolution
                    assertException(() ->
                                    (new PrinterCapabilitiesInfo.Builder(printerId))
                                            .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                                    PrintAttributes.COLOR_MODE_COLOR)
                                            .setMinMargins(DEFAULT_MARGINS)
                                            .addMediaSize(MediaSize.ISO_A4, true).build(),
                            IllegalStateException.class);
                    // Test no default resolution
                    assertException(() ->
                                    (new PrinterCapabilitiesInfo.Builder(printerId))
                                            .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                                    PrintAttributes.COLOR_MODE_COLOR)
                                            .setMinMargins(DEFAULT_MARGINS)
                                            .addMediaSize(MediaSize.ISO_A4, true)
                                            .addResolution(RESOLUTION_300, false).build(),
                            IllegalStateException.class);
                    // Test two default resolutions
                    assertException(() ->
                                    (new PrinterCapabilitiesInfo.Builder(printerId))
                                            .addResolution(RESOLUTION_300, true)
                                            .addResolution(RESOLUTION_600, true),
                            IllegalArgumentException.class);

                    onPrinterDiscoverySessionCreateCalled();
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

        PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(null, null, null);

        // Start printing.
        print(adapter);

        waitForPrinterDiscoverySessionCreateCallbackCalled();

        getActivity().finish();

        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    /**
     * That that you can use all sane legal PrinterCapabilityInfos.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void sanePrinterCapabilityInfos() throws Exception {
        final PrinterDiscoverySessionCallbacks firstSessionCallbacks =
                createMockPrinterDiscoverySessionCallbacks(invocation -> {
                    StubbablePrinterDiscoverySession session =
                            ((PrinterDiscoverySessionCallbacks)
                                    invocation.getMock()).getSession();

                    MediaSize[] mediaSizes = {MediaSize.ISO_A0, MediaSize.ISO_A0,
                            MediaSize.ISO_A1};
                    Resolution[] resolutions = {RESOLUTION_300, RESOLUTION_300,
                            RESOLUTION_600};
                    int[] colorModes = {PrintAttributes.COLOR_MODE_MONOCHROME,
                            PrintAttributes.COLOR_MODE_COLOR};
                    int[] duplexModes = {PrintAttributes.DUPLEX_MODE_NONE,
                            PrintAttributes.DUPLEX_MODE_LONG_EDGE,
                            PrintAttributes.DUPLEX_MODE_SHORT_EDGE};

                    ArrayList<PrinterInfo> printers = new ArrayList<>();
                    for (int mediaSizeIndex = 1; mediaSizeIndex < mediaSizes.length;
                         mediaSizeIndex++) {
                        for (int resolutionIndex = 1; resolutionIndex < mediaSizes.length;
                             resolutionIndex++) {
                            for (int colorIndex = 1; colorIndex < colorModes.length;
                                 colorIndex++) {
                                for (int duplexIndex = 1; duplexIndex < duplexModes.length;
                                     duplexIndex++) {
                                    PrinterId printerId = session.getService()
                                            .generatePrinterId(Integer.valueOf(printers.size())
                                                    .toString());

                                    // Setup capabilities
                                    PrinterCapabilitiesInfo.Builder b =
                                            new PrinterCapabilitiesInfo.Builder(printerId);

                                    for (int i = 0; i < mediaSizeIndex; i++) {
                                        b.addMediaSize(mediaSizes[i], i == mediaSizeIndex - 1);
                                    }

                                    for (int i = 0; i < resolutionIndex; i++) {
                                        b.addResolution(resolutions[i],
                                                i == resolutionIndex - 1);
                                    }

                                    int allColors = 0;
                                    for (int i = 0; i < colorIndex; i++) {
                                        allColors |= colorModes[i];
                                    }
                                    b.setColorModes(allColors, colorModes[colorIndex - 1]);

                                    int allDuplexModes = 0;
                                    for (int i = 0; i < duplexIndex; i++) {
                                        allDuplexModes |= duplexModes[i];
                                    }
                                    b.setDuplexModes(allDuplexModes,
                                            duplexModes[duplexIndex - 1]);

                                    b.setMinMargins(DEFAULT_MARGINS);

                                    // Create printer
                                    PrinterInfo printer = (new PrinterInfo.Builder(printerId,
                                            Integer.valueOf(printers.size()).toString(),
                                            PrinterInfo.STATUS_IDLE)).setCapabilities(b.build())
                                            .build();

                                    // Verify capabilities
                                    PrinterCapabilitiesInfo cap = printer.getCapabilities();

                                    assertEquals(mediaSizeIndex, cap.getMediaSizes().size());
                                    assertEquals(mediaSizes[mediaSizeIndex - 1],
                                            cap.getDefaults().getMediaSize());
                                    for (int i = 0; i < mediaSizeIndex; i++) {
                                        assertTrue(cap.getMediaSizes().contains(mediaSizes[i]));
                                    }

                                    assertEquals(resolutionIndex, cap.getResolutions().size());
                                    assertEquals(resolutions[resolutionIndex - 1],
                                            cap.getDefaults().getResolution());
                                    for (int i = 0; i < resolutionIndex; i++) {
                                        assertTrue(cap.getResolutions().contains(resolutions[i]));
                                    }

                                    assertEquals(allColors, cap.getColorModes());
                                    assertEquals(colorModes[colorIndex - 1],
                                            cap.getDefaults().getColorMode());

                                    assertEquals(allDuplexModes, cap.getDuplexModes());
                                    assertEquals(duplexModes[duplexIndex - 1],
                                            cap.getDefaults().getDuplexMode());

                                    assertEquals(DEFAULT_MARGINS, cap.getMinMargins());

                                    // Add printer
                                    printers.add(printer);
                                }
                            }
                        }
                    }

                    session.addPrinters(printers);

                    onPrinterDiscoverySessionCreateCalled();
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

        PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(null, null, null);

        // Start printing.
        print(adapter);

        waitForPrinterDiscoverySessionCreateCallbackCalled();

        getUiDevice().pressBack();

        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    /**
     * Base test that performs a print operation with a give PrinterCapabilityInfo and run a test
     * function before finishing.
     *
     * @throws Exception
     */
    private void testPrinterCapabilityInfo(final Function<PrinterId, PrinterCapabilitiesInfo>
            capBuilder, Consumer<PrintAttributes> test) throws Exception {
        final PrinterDiscoverySessionCallbacks firstSessionCallbacks =
                createMockPrinterDiscoverySessionCallbacks(invocation -> {
                    StubbablePrinterDiscoverySession session =
                            ((PrinterDiscoverySessionCallbacks)
                                    invocation.getMock()).getSession();

                    PrinterId printerId = session.getService()
                            .generatePrinterId(PRINTER_NAME);

                    ArrayList<PrinterInfo> printers = new ArrayList<>();
                    printers.add((new PrinterInfo.Builder(printerId, PRINTER_NAME,
                            PrinterInfo.STATUS_IDLE))
                            .setCapabilities(capBuilder.apply(printerId)).build());

                    session.addPrinters(printers);

                    onPrinterDiscoverySessionCreateCalled();
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

        final PrintAttributes[] layoutAttributes = new PrintAttributes[1];

        PrintDocumentAdapter adapter = createMockPrintDocumentAdapter(
                invocation -> {
                    LayoutResultCallback callback = (LayoutResultCallback) invocation
                            .getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setPageCount(1)
                            .build();
                    layoutAttributes[0] = (PrintAttributes) invocation.getArguments()[1];

                    callback.onLayoutFinished(info, true);
                    return null;
                },
                invocation -> {
                    Object[] args = invocation.getArguments();
                    PageRange[] pages = (PageRange[]) args[0];
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];

                    writeBlankPages(layoutAttributes[0], fd, pages[0].getStart(),
                            pages[0].getEnd());
                    fd.close();

                    callback.onWriteFinished(pages);
                    return null;
                }, null);

        // Start printing.
        print(adapter);

        // make sure that options does not crash
        openPrintOptions();

        if (!sDefaultPrinterBeenSet) {
            // Select printer under test
            selectPrinter(PRINTER_NAME);
        }

        clickPrintButton();

        if (!sDefaultPrinterBeenSet) {
            answerPrintServicesWarning(true);
            sDefaultPrinterBeenSet = true;
        }

        test.accept(layoutAttributes[0]);

        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    /**
     * That that you use a default color that is not in the allowed colors. This is allowed because
     * of historical reasons.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void invalidDefaultColor() throws Exception {
        testPrinterCapabilityInfo(
                (printerId) -> (new PrinterCapabilitiesInfo.Builder(printerId))
                        .addMediaSize(MediaSize.ISO_A4, true)
                        .addResolution(RESOLUTION_300, true)
                        .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME,
                                PrintAttributes.COLOR_MODE_COLOR).build(),
                (layoutAttributes) -> assertEquals(layoutAttributes.getColorMode(),
                        PrintAttributes.COLOR_MODE_MONOCHROME));
    }

    /**
     * That that you use a default duplex mode that is not in the allowed duplex modes. This is
     * allowed because of historical reasons.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void invalidDefaultDuplexMode() throws Exception {
        testPrinterCapabilityInfo(
                (printerId) -> (new PrinterCapabilitiesInfo.Builder(printerId))
                        .addMediaSize(MediaSize.ISO_A4, true)
                        .addResolution(RESOLUTION_300, true)
                        .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME,
                                PrintAttributes.COLOR_MODE_MONOCHROME)
                        .setDuplexModes(PrintAttributes.DUPLEX_MODE_LONG_EDGE
                                | PrintAttributes.DUPLEX_MODE_NONE,
                                PrintAttributes.DUPLEX_MODE_SHORT_EDGE).build(),
                (layoutAttributes) -> assertTrue(layoutAttributes.getDuplexMode() ==
                        PrintAttributes.DUPLEX_MODE_LONG_EDGE || layoutAttributes.getDuplexMode() ==
                        PrintAttributes.DUPLEX_MODE_NONE));
    }
}
