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

import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test that the print attributes are correctly propagated through the print framework
 */
@RunWith(AndroidJUnit4.class)
public class PrintAttributesTest extends BasePrintTest {
    private static final String LOG_TAG = "PrintAttributesTest";
    private final String PRINTER_NAME = "Test printer";

    private final Margins[] MIN_MARGINS = {
            new Margins(0, 0, 0, 0), new Margins(10, 10, 10, 10), new Margins(20, 20, 20, 20),
    };

    private final MediaSize MEDIA_SIZES[] = {
            MediaSize.ISO_A3, MediaSize.ISO_A4, MediaSize.ISO_A5
    };

    private final int COLOR_MODES[] = {
            PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_COLOR
    };

    private final int DUPLEX_MODES[] = {
            PrintAttributes.DUPLEX_MODE_NONE, PrintAttributes.DUPLEX_MODE_LONG_EDGE,
            PrintAttributes.DUPLEX_MODE_SHORT_EDGE
    };

    private final Resolution RESOLUTIONS[] = {
            new Resolution("300x300", "300x300", 300, 300),
            new Resolution("600x600", "600x600", 600, 600),
            new Resolution("1200x1200", "1200x1200", 1200, 1200)
    };

    /**
     * Stores the {@link PrintAttributes} passed to the layout method
     */
    private PrintAttributes mLayoutAttributes;
    private static boolean sHasBeenSetup;

    /**
     * Create a new {@link PrintAttributes} object with the given properties.
     *
     * All properties can be null/0 to remain unset.
     *
     * @param mediaSize {@link MediaSize} to use
     * @param colorMode Color mode to use
     * @param duplexMode Duplex mode to use
     * @param resolution {@link Resolution} to use
     *
     * @return The newly created object or null if no properties are set
     */
    private PrintAttributes createAttributes(MediaSize mediaSize, int colorMode, int duplexMode,
            Resolution resolution) {
        if (mediaSize == null && colorMode == 0 && duplexMode == 0 && resolution == null) {
            return null;
        }

        PrintAttributes.Builder builder = new PrintAttributes.Builder();

        if (mediaSize != null) {
            builder.setMediaSize(mediaSize);
        }

        if (colorMode != 0) {
            builder.setColorMode(colorMode);
        }

        if (duplexMode != 0) {
            builder.setDuplexMode(duplexMode);
        }

        if (resolution != null) {
            builder.setResolution(resolution);
        }

        return builder.build();
    }

    /**
     * Create {@link PrinterDiscoverySessionCallbacks} with a single printer that has the given
     * capabilities
     *
     * @param minMargins The minMargins of the printer
     * @param mediaSizes The {@link MediaSize media sizes} supported by the printer
     * @param defaultMediaSize The default {@link MediaSize}
     * @param colorModes The color modes supported by the printer
     * @param defaultColorMode The default color mode
     * @param duplexModes The duplex modes supported by the printer
     * @param defaultDuplexMode The default duplex mode
     * @param resolutions The {@link Resolution resolutions} supported by the printer
     * @param defaultResolution The default {@link Resolution} to use
     *
     * @return New {@link PrinterDiscoverySessionCallbacks} with a single printer that has the
     *         given capabilities
     */
    private PrinterDiscoverySessionCallbacks createMockPrinterDiscoverySessionCallbacks(
            final Margins minMargins, final MediaSize mediaSizes[],
            final MediaSize defaultMediaSize, final int colorModes[], final int defaultColorMode,
            final int duplexModes[], final int defaultDuplexMode, final Resolution resolutions[],
            final Resolution defaultResolution) {
        return createMockPrinterDiscoverySessionCallbacks(invocation -> {
            StubbablePrinterDiscoverySession session =
                    ((PrinterDiscoverySessionCallbacks) invocation.getMock()).getSession();

            if (session.getPrinters().isEmpty()) {
                List<PrinterInfo> printers = new ArrayList<>();
                PrinterId printerId = session.getService().generatePrinterId(PRINTER_NAME);

                PrinterCapabilitiesInfo.Builder builder =
                        new PrinterCapabilitiesInfo.Builder(printerId);

                builder.setMinMargins(minMargins);

                int mediaSizesLength = mediaSizes.length;
                for (int i = 0; i < mediaSizesLength; i++) {
                    if (mediaSizes[i].equals(defaultMediaSize)) {
                        builder.addMediaSize(mediaSizes[i], true);
                    } else {
                        builder.addMediaSize(mediaSizes[i], false);
                    }
                }

                int colorModesMask = 0;
                int colorModesLength = colorModes.length;
                for (int i = 0; i < colorModesLength; i++) {
                    colorModesMask |= colorModes[i];
                }
                builder.setColorModes(colorModesMask, defaultColorMode);

                int duplexModesMask = 0;
                int duplexModeLength = duplexModes.length;
                for (int i = 0; i < duplexModeLength; i++) {
                    duplexModesMask |= duplexModes[i];
                }
                builder.setDuplexModes(duplexModesMask, defaultDuplexMode);

                int resolutionsLength = resolutions.length;
                for (int i = 0; i < resolutionsLength; i++) {
                    if (resolutions[i].equals(defaultResolution)) {
                        builder.addResolution(resolutions[i], true);
                    } else {
                        builder.addResolution(resolutions[i], false);
                    }
                }

                PrinterInfo printer = new PrinterInfo.Builder(printerId, PRINTER_NAME,
                        PrinterInfo.STATUS_IDLE).setCapabilities(builder.build()).build();
                printers.add(printer);

                session.addPrinters(printers);
            }
            return null;
        }, null, null, invocation -> null, null, null, invocation -> {
            // Take a note onDestroy was called.
            onPrinterDiscoverySessionDestroyCalled();
            return null;
        });
    }

    /**
     * Create dummy {@link PrintServiceCallbacks}
     *
     * This is needed to as the print framework is trying to talk to any printer even if is not set
     * up.
     *
     * @return Dummy {@link PrintServiceCallbacks}
     */
    private PrintServiceCallbacks createDummyMockPrintServiceCallbacks() {
        return createMockPrintServiceCallbacks(null, null, null);
    }

    /**
     * Create a {@link PrintDocumentAdapter} that serves empty pages
     *
     * @return A new {@link PrintDocumentAdapter}
     */
    private PrintDocumentAdapter createMockPrintDocumentAdapter() {
        return createMockPrintDocumentAdapter(
                invocation -> {
                    mLayoutAttributes = (PrintAttributes) invocation.getArguments()[1];
                    LayoutResultCallback callback =
                            (LayoutResultCallback) invocation.getArguments()[3];
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
                    writeBlankPages(mLayoutAttributes, fd, pages[0].getStart(),
                            pages[0].getEnd());
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
    }

    /**
     * Set up a single printer with the given capabilities
     *
     * @param minMargins The minMargins of the printer
     * @param mediaSizes The {@link MediaSize media sizes} supported by the printer
     * @param defaultMediaSize The default {@link MediaSize}
     * @param colorModes The color modes supported by the printer
     * @param defaultColorMode The default color mode
     * @param duplexModes The duplex modes supported by the printer
     * @param defaultDuplexMode The default duplex mode
     * @param resolutions The {@link Resolution resolutions} supported by the printer
     * @param defaultResolution The default {@link Resolution} to use
     *
     * @return A {@link PrintDocumentAdapter} that can be used for the new printer
     */
    private PrintDocumentAdapter setUpPrinter(Margins minMargins, MediaSize mediaSizes[],
            MediaSize defaultMediaSize, int colorModes[], int defaultColorMode, int duplexModes[],
            int defaultDuplexMode, Resolution resolutions[], Resolution defaultResolution) {
        final PrinterDiscoverySessionCallbacks sessionCallbacks =
                createMockPrinterDiscoverySessionCallbacks(minMargins, mediaSizes,
                        defaultMediaSize, colorModes, defaultColorMode, duplexModes,
                        defaultDuplexMode, resolutions, defaultResolution);

        PrintServiceCallbacks serviceCallbacks = createMockPrintServiceCallbacks(
                invocation -> sessionCallbacks,
                invocation -> {
                    PrintJob printJob = (PrintJob) invocation.getArguments()[0];
                    // We pretend the job is handled immediately.
                    printJob.complete();
                    return null;
                }, null);

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // We need to set up the second print service too, otherwise we get a null pointer in the
        // print framework
        SecondPrintService.setCallbacks(createDummyMockPrintServiceCallbacks());

        // Create a print adapter that respects the print contract.
        return createMockPrintDocumentAdapter();
    }

    /**
     * Check if a value is in an array.
     *
     * To be use instead of Arrays.asList(array).contains(value) for ints.
     *
     * @param array The array the value might be in
     * @param value The value to search for
     *
     * @return true iff the value is in the array
     */
    private boolean isInArray(final int array[], int value) {
        int arrayLength = array.length;
        for (int i = 0; i < arrayLength; i++) {
            if (array[i] == value) {
                return true;
            }
        }

        return false;
    }

    @Before
    public void setUpServicesAndAdapter() throws Exception {
        if (!sHasBeenSetup) {
            // Set up printer with supported and default attributes
            PrintDocumentAdapter adapter =
                    setUpPrinter(MIN_MARGINS[0], MEDIA_SIZES, MEDIA_SIZES[0], COLOR_MODES,
                            COLOR_MODES[0], DUPLEX_MODES, DUPLEX_MODES[0], RESOLUTIONS,
                            RESOLUTIONS[0]);

            Log.d(LOG_TAG, "makeDefaultPrinter");
            // Make printer default. This is necessary as a different default printer might pre-select
            // its default attributes and thereby overrides the defaults of the tested printer.
            makeDefaultPrinter(adapter, PRINTER_NAME);

            sHasBeenSetup = true;
        }

        resetCounters();
    }

    /**
     * Flexible base test for all print attribute tests.
     *
     * Asserts that the default and suggested attributes are properly honored by the print
     * framework.
     *
     * @param minMargins The minMargins of the printer
     * @param mediaSizes The {@link MediaSize media sizes} supported by the printer
     * @param defaultMediaSize The default {@link MediaSize}
     * @param colorModes The color modes supported by the printer
     * @param defaultColorMode The default color mode
     * @param duplexModes The duplex modes supported by the printer
     * @param defaultDuplexMode The default duplex mode
     * @param resolutions The {@link Resolution resolutions} supported by the printer
     * @param defaultResolution The default {@link Resolution} to use
     * @param suggestedMediaSize The suggested {@link MediaSize} for the print job
     * @param suggestedColorMode The suggested color mode for the print job
     * @param suggestedDuplexMode The suggested duplex mode for the print job
     * @param suggestedResolution The suggested resolution for the print job
     *
     * @throws Exception If anything is unexpected
     */
    private void baseTest(Margins minMargins, MediaSize mediaSizes[],
            MediaSize defaultMediaSize, MediaSize suggestedMediaSize, int colorModes[],
            int defaultColorMode, int suggestedColorMode, int duplexModes[],
            int defaultDuplexMode, int suggestedDuplexMode, Resolution resolutions[],
            Resolution defaultResolution, Resolution suggestedResolution) throws Exception {
        PrintDocumentAdapter adapter =
                setUpPrinter(minMargins, mediaSizes, defaultMediaSize, colorModes, defaultColorMode,
                        duplexModes, defaultDuplexMode, resolutions, defaultResolution);

        // Select suggested attributes
        PrintAttributes suggestedAttributes = createAttributes(suggestedMediaSize,
                suggestedColorMode, suggestedDuplexMode, suggestedResolution);

        // Start print action and wait for layout, the result is stored in #layoutAttributes,
        // @see createMockPrintDocumentAdapter
        Log.d(LOG_TAG, "print");
        print(adapter, suggestedAttributes);
        Log.d(LOG_TAG, "waitForWriteAdapterCallback");
        waitForWriteAdapterCallback(1);
        Log.d(LOG_TAG, "clickPrintButton");
        clickPrintButton();
        Log.d(LOG_TAG, "waitForPrinterDiscoverySessionDestroyCallbackCalled");
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        // It does not make sense to suggest minMargins, hence the print framework always picks
        // the one set up for the printer.
        assertEquals("Min margins not as expected", minMargins, mLayoutAttributes.getMinMargins());

        // Verify that the attributes are honored properly
        if (suggestedMediaSize != null && Arrays.asList(mediaSizes).contains(suggestedMediaSize)) {
            assertEquals("Media size not as suggested", suggestedMediaSize,
                    mLayoutAttributes.getMediaSize());
        } else {
            assertEquals("Media size not default", defaultMediaSize,
                    mLayoutAttributes.getMediaSize());
        }

        if (suggestedColorMode != 0 && isInArray(colorModes, suggestedColorMode)) {
            assertEquals("Color mode not as suggested", suggestedColorMode,
                    mLayoutAttributes.getColorMode());
        } else {
            assertEquals("Color mode not default", defaultColorMode,
                    mLayoutAttributes.getColorMode());
        }

        if (suggestedDuplexMode != 0 && isInArray(duplexModes, suggestedDuplexMode)) {
            assertEquals("Duplex mode not as suggested", suggestedDuplexMode,
                    mLayoutAttributes.getDuplexMode());
        } else {
            assertEquals("Duplex mode not default", defaultDuplexMode,
                    mLayoutAttributes.getDuplexMode());
        }

        if (suggestedResolution != null
                && Arrays.asList(resolutions).contains(suggestedResolution)) {
            assertEquals("Resolution not as suggested", suggestedResolution,
                    mLayoutAttributes.getResolution());
        } else {
            assertEquals("Resolution not default", defaultResolution,
                    mLayoutAttributes.getResolution());
        }
    }

    /**
     * Test that attributes are as expected if the default attributes match the suggested ones.
     *
     * This test sets the default and suggested attributes to the first selection.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void defaultMatchesSuggested0() throws Exception {
        //       available     default          suggestion
        baseTest(              MIN_MARGINS[0],
                 MEDIA_SIZES,  MEDIA_SIZES[0],  MEDIA_SIZES[0],
                 COLOR_MODES,  COLOR_MODES[0],  COLOR_MODES[0],
                 DUPLEX_MODES, DUPLEX_MODES[0], DUPLEX_MODES[0],
                 RESOLUTIONS,  RESOLUTIONS[0],  RESOLUTIONS[0]);
    }

    /**
     * Test that attributes are as expected if the default attributes match the suggested ones.
     *
     * This test sets the default and suggested attributes to the second selection.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void defaultMatchesSuggested1() throws Exception {
        //       available     default          suggestion
        baseTest(              MIN_MARGINS[1],
                 MEDIA_SIZES,  MEDIA_SIZES[1],  MEDIA_SIZES[1],
                 COLOR_MODES,  COLOR_MODES[1],  COLOR_MODES[1],
                 DUPLEX_MODES, DUPLEX_MODES[1], DUPLEX_MODES[1],
                 RESOLUTIONS,  RESOLUTIONS[1],  RESOLUTIONS[1]);
    }

    /**
     * Test that attributes are as expected if the default attributes match the suggested ones.
     *
     * This test sets the default and suggested attributes to the third selection.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void defaultMatchesSuggested2() throws Exception {
        //       available     default          suggestion
        baseTest(              MIN_MARGINS[2],
                 MEDIA_SIZES,  MEDIA_SIZES[2],  MEDIA_SIZES[2],
                 // There are only two color modes, hence pick [1]
                 COLOR_MODES,  COLOR_MODES[1],  COLOR_MODES[1],
                 DUPLEX_MODES, DUPLEX_MODES[2], DUPLEX_MODES[2],
                 RESOLUTIONS,  RESOLUTIONS[2],  RESOLUTIONS[2]);
    }

    /**
     * Test that attributes are as expected if the no suggestion is given.
     *
     * This test sets the default attributes to the first selection.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void noSuggestion0() throws Exception {
        //       available     default          suggestion
        baseTest(              MIN_MARGINS[0],
                 MEDIA_SIZES,  MEDIA_SIZES[0],  null,
                 COLOR_MODES,  COLOR_MODES[0],  0,
                 DUPLEX_MODES, DUPLEX_MODES[0], 0,
                 RESOLUTIONS,  RESOLUTIONS[0],  null);
    }

    /**
     * Test that attributes are as expected if the no suggestion is given.
     *
     * This test sets the default attributes to the second selection.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void noSuggestion1() throws Exception {
        //       available     default          suggestion
        baseTest(              MIN_MARGINS[1],
                 MEDIA_SIZES,  MEDIA_SIZES[1],  null,
                 COLOR_MODES,  COLOR_MODES[1],  0,
                 DUPLEX_MODES, DUPLEX_MODES[1], 0,
                 RESOLUTIONS,  RESOLUTIONS[1],  null);
    }

    /**
     * Test that attributes are as expected if the no suggestion is given.
     *
     * This test sets the default attributes to the third selection.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void noSuggestion2() throws Exception {
        //       available     default          suggestion
        baseTest(              MIN_MARGINS[2],
                 MEDIA_SIZES,  MEDIA_SIZES[2],  null,
                 // There are only two color modes, hence pick [1]
                 COLOR_MODES,  COLOR_MODES[1],  0,
                 DUPLEX_MODES, DUPLEX_MODES[2], 0,
                 RESOLUTIONS,  RESOLUTIONS[2],  null);
    }

    /**
     * Test that attributes are as expected if only the {@link MediaSize} is suggested.
     *
     * This test sets the default attributes to the first selection, but the {@link MediaSize} is
     * suggested to be the second selection.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void mediaSizeSuggestion0() throws Exception {
        //       available     default          suggestion
        baseTest(              MIN_MARGINS[0],
                 MEDIA_SIZES,  MEDIA_SIZES[0],  MEDIA_SIZES[1],
                 COLOR_MODES,  COLOR_MODES[0],  0,
                 DUPLEX_MODES, DUPLEX_MODES[0], 0,
                 RESOLUTIONS,  RESOLUTIONS[0],  null);
    }

    /**
     * Test that attributes are as expected if only the {@link MediaSize} is suggested.
     *
     * This test sets the default attributes to the second selection, but the {@link MediaSize} is
     * suggested to be the first selection.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void mediaSizeSuggestion1() throws Exception {
        //       available     default          suggestion
        baseTest(              MIN_MARGINS[1],
                 MEDIA_SIZES,  MEDIA_SIZES[1],  MEDIA_SIZES[0],
                 COLOR_MODES,  COLOR_MODES[1],  0,
                 DUPLEX_MODES, DUPLEX_MODES[1], 0,
                 RESOLUTIONS,  RESOLUTIONS[1],  null);
    }

    /**
     * Test that attributes are as expected if only the duplex mode is suggested.
     *
     * This test sets the default attributes to the first selection, but the duplex mode is
     * suggested to be the second selection.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void duplexModeSuggestion0() throws Exception {
        //       available     default          suggestion
        baseTest(              MIN_MARGINS[0],
                 MEDIA_SIZES,  MEDIA_SIZES[0],  null,
                 COLOR_MODES,  COLOR_MODES[0],  0,
                 DUPLEX_MODES, DUPLEX_MODES[0], DUPLEX_MODES[1],
                 RESOLUTIONS,  RESOLUTIONS[0],  null);
    }

    /**
     * Test that attributes are as expected if only the duplex mode is suggested.
     *
     * This test sets the default attributes to the second selection, but the duplex mode is
     * suggested to be the first selection.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void duplexModeSuggestion1() throws Exception {
        //       available     default          suggestion
        baseTest(              MIN_MARGINS[1],
                 MEDIA_SIZES,  MEDIA_SIZES[1],  null,
                 COLOR_MODES,  COLOR_MODES[1],  0,
                 DUPLEX_MODES, DUPLEX_MODES[1], DUPLEX_MODES[0],
                 RESOLUTIONS,  RESOLUTIONS[1],  null);
    }

    /**
     * Test that attributes are as expected if all attributes are suggested and different from the
     * default attributes.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void suggestedDifferentFromDefault() throws Exception {
        //       available     default          suggestion
        baseTest(              MIN_MARGINS[0],
                 MEDIA_SIZES,  MEDIA_SIZES[0],  MEDIA_SIZES[1],
                 COLOR_MODES,  COLOR_MODES[0],  COLOR_MODES[1],
                 DUPLEX_MODES, DUPLEX_MODES[0], DUPLEX_MODES[1],
                 RESOLUTIONS,  RESOLUTIONS[0],  RESOLUTIONS[1]);
    }

    /**
     * Test that attributes are as expected if all attributes are suggested but all of them are not
     * supported by the printer.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void unsupportedSuggested() throws Exception {
        //       available                               default          suggestion
        baseTest(                                        MIN_MARGINS[0],
                 Arrays.copyOfRange(MEDIA_SIZES, 0, 1),  MEDIA_SIZES[0],  MEDIA_SIZES[1],
                 Arrays.copyOfRange(COLOR_MODES, 0, 1),  COLOR_MODES[0],  COLOR_MODES[1],
                 Arrays.copyOfRange(DUPLEX_MODES, 0, 1), DUPLEX_MODES[0], DUPLEX_MODES[1],
                 Arrays.copyOfRange(RESOLUTIONS, 0, 1),  RESOLUTIONS[0],  RESOLUTIONS[1]);
    }

    /**
     * Test that negative Margins do not cause issues in the print print spooler. Negative margins
     * are allowed because of historical reasons.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void negativeMargins() throws Exception {
        //       available     default                          suggestion
        baseTest(              new Margins(-10, -10, -10, -10),
                 MEDIA_SIZES,  MEDIA_SIZES[1],                  null,
                 COLOR_MODES,  COLOR_MODES[1],                  0,
                 DUPLEX_MODES, DUPLEX_MODES[1],                 0,
                 RESOLUTIONS,  RESOLUTIONS[1],                  null);
    }
}
