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
import android.print.PrintJobInfo;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.cts.services.CustomPrintOptionsActivity;
import android.print.cts.services.FirstPrintService;
import android.print.cts.services.PrintServiceCallbacks;
import android.print.cts.services.PrinterDiscoverySessionCallbacks;
import android.print.cts.services.SecondPrintService;
import android.print.cts.services.StubbablePrinterDiscoverySession;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.util.Log;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static android.print.cts.Utils.eventually;

/**
 * This test verifies changes to the printer capabilities are applied correctly.
 */
@RunWith(AndroidJUnit4.class)
public class CustomPrintOptionsTest extends BasePrintTest {
    private final static String LOG_TAG = "CustomPrintOptionsTest";
    private static final String PRINTER_NAME = "Test printer";
    private static final int MAX_TRIES = 10;

    // Default settings
    private final PageRange[] DEFAULT_PAGES = new PageRange[] { new PageRange(0, 0) };
    private final MediaSize DEFAULT_MEDIA_SIZE = MediaSize.ISO_A0;
    private final int DEFAULT_COLOR_MODE = PrintAttributes.COLOR_MODE_COLOR;
    private final int DEFAULT_DUPLEX_MODE = PrintAttributes.DUPLEX_MODE_LONG_EDGE;
    private final Resolution DEFAULT_RESOLUTION = new Resolution("300x300", "300x300", 300, 300);
    private final Margins DEFAULT_MARGINS = new Margins(0, 0, 0, 0);

    // All settings that are tested
    private final PageRange[][] PAGESS = { DEFAULT_PAGES, new PageRange[] { new PageRange(1, 1) },
            new PageRange[] { new PageRange(0, 2) }
    };
    private final MediaSize[] MEDIA_SIZES = { DEFAULT_MEDIA_SIZE, MediaSize.ISO_B0 };
    private final Integer[] COLOR_MODES = { DEFAULT_COLOR_MODE,
            PrintAttributes.COLOR_MODE_MONOCHROME
    };
    private final Integer[] DUPLEX_MODES = { DEFAULT_DUPLEX_MODE, PrintAttributes.DUPLEX_MODE_NONE
    };
    private final Resolution[] RESOLUTIONS = { DEFAULT_RESOLUTION,
            new Resolution("600x600", "600x600", 600, 600)
    };

    private PrintAttributes mLayoutAttributes;
    private PrintDocumentAdapter mAdapter;
    private static boolean sHasDefaultPrinterSet;

    /**
     * Get the page ranges currently selected as described in the UI.
     *
     * @return Only page ranges from {@link #PAGESS} are detected correctly.
     *
     * @throws Exception If something was unexpected
     */
    private PageRange[] getPages() throws Exception {
        if (getUiDevice().hasObject(By.text(getPrintSpoolerStringOneParam("template_all_pages",
                3)))) {
            return PAGESS[2];
        }

        try {
            UiObject pagesEditText = getUiDevice().findObject(new UiSelector().resourceId(
                    "com.android.printspooler:id/page_range_edittext"));

            if (pagesEditText.getText().equals("2")) {
                return PAGESS[1];
            }

            if (pagesEditText.getText().equals("1")) {
                return PAGESS[0];
            }

            return null;
        } catch (UiObjectNotFoundException e) {
            dumpWindowHierarchy();
            throw e;
        }
    }

    @Before
    public void setUpServicesAndAdapter() {
        final PrinterDiscoverySessionCallbacks firstSessionCallbacks =
                createMockPrinterDiscoverySessionCallbacks(invocation -> {
                    StubbablePrinterDiscoverySession session =
                            ((PrinterDiscoverySessionCallbacks) invocation.getMock())
                                    .getSession();
                    PrinterId printerId = session.getService().generatePrinterId(PRINTER_NAME);
                    List<PrinterInfo> printers = new ArrayList<>(1);
                    PrinterCapabilitiesInfo.Builder builder =
                            new PrinterCapabilitiesInfo.Builder(printerId);

                    builder.setMinMargins(DEFAULT_MARGINS)
                            .setColorModes(COLOR_MODES[0] | COLOR_MODES[1],
                                    DEFAULT_COLOR_MODE)
                            .setDuplexModes(DUPLEX_MODES[0] | DUPLEX_MODES[1],
                                    DEFAULT_DUPLEX_MODE)
                            .addMediaSize(DEFAULT_MEDIA_SIZE, true)
                            .addMediaSize(MEDIA_SIZES[1], false)
                            .addResolution(DEFAULT_RESOLUTION, true)
                            .addResolution(RESOLUTIONS[1], false);

                    printers.add(new PrinterInfo.Builder(printerId, PRINTER_NAME,
                            PrinterInfo.STATUS_IDLE).setCapabilities(builder.build()).build());

                    session.addPrinters(printers);
                    return null;
                }, null, null, null, null, null, invocation -> {
                    onPrinterDiscoverySessionDestroyCalled();
                    return null;
                });

        mAdapter = createMockPrintDocumentAdapter(
                invocation -> {
                    LayoutResultCallback callback = (LayoutResultCallback) invocation
                            .getArguments()[3];
                    PrintDocumentInfo info = new PrintDocumentInfo.Builder(PRINT_JOB_NAME)
                            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                            .setPageCount(3)
                            .build();

                    synchronized (CustomPrintOptionsTest.this) {
                        mLayoutAttributes = (PrintAttributes) invocation.getArguments()[1];

                        CustomPrintOptionsTest.this.notifyAll();
                    }

                    callback.onLayoutFinished(info, true);
                    return null;
                },
                invocation -> {
                    Object[] args = invocation.getArguments();
                    ParcelFileDescriptor fd = (ParcelFileDescriptor) args[1];
                    WriteResultCallback callback = (WriteResultCallback) args[3];

                    PageRange[] writtenPages = (PageRange[]) args[0];

                    writeBlankPages(mLayoutAttributes, fd, writtenPages[0].getStart(),
                            writtenPages[0].getEnd());
                    fd.close();

                    callback.onWriteFinished(writtenPages);

                    onWriteCalled();

                    return null;
                }, null);

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks firstServiceCallbacks = createMockPrintServiceCallbacks(
                invocation -> firstSessionCallbacks, null, null);

        // Configure the print services.
        FirstPrintService.setCallbacks(firstServiceCallbacks);
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        // Set default printer
        if (!sHasDefaultPrinterSet) {
            // This is the first print test that runs. If this is run after other tests these other
            // test can still cause memory pressure and make the printactivity to go through a
            // destroy-create cycle. In this case we have to retry to operation.
            int tries = 0;
            while (true) {
                try {
                    resetCounters();
                    makeDefaultPrinter(mAdapter, PRINTER_NAME);
                    break;
                } catch (Throwable e) {
                    if (getActivityDestroyCallbackCallCount() > 0 && tries < MAX_TRIES) {
                        Log.e(LOG_TAG, "Activity was destroyed during test, retrying", e);

                        tries++;
                        continue;
                    }

                    throw new RuntimeException(e);
                }
            }

            sHasDefaultPrinterSet = true;
        }
    }

    /**
     * Test that we can switch to a specific set of settings via the custom print options activity
     *
     * @param copyFromOriginal If the print job info should be copied from the original
     * @param numCopies        The copies to print
     * @param pages            The page ranges to print
     * @param mediaSize        The media size to use
     * @param isPortrait       If the mediaSize is portrait
     * @param colorMode        The color mode to use
     * @param duplexMode       The duplex mode to use
     * @param resolution       The resolution to use
     *
     * @throws Exception If anything is unexpected
     */
    private void testCase(final boolean copyFromOriginal, final Integer numCopies,
            final PageRange[] pages, final MediaSize mediaSize, final boolean isPortrait,
            final Integer colorMode, final Integer duplexMode, final Resolution resolution)
            throws Throwable {
        final PrintAttributes.Builder additionalAttributesBuilder = new PrintAttributes.Builder();
        final PrintAttributes.Builder newAttributesBuilder = new PrintAttributes.Builder();

        newAttributesBuilder.setMinMargins(DEFAULT_MARGINS);

        if (mediaSize != null) {
            if (isPortrait) {
                additionalAttributesBuilder.setMediaSize(mediaSize.asPortrait());
                newAttributesBuilder.setMediaSize(mediaSize.asPortrait());
            } else {
                additionalAttributesBuilder.setMediaSize(mediaSize.asLandscape());
                newAttributesBuilder.setMediaSize(mediaSize.asLandscape());
            }
        } else {
            newAttributesBuilder.setMediaSize(DEFAULT_MEDIA_SIZE);
        }

        if (colorMode != null) {
            additionalAttributesBuilder.setColorMode(colorMode);
            newAttributesBuilder.setColorMode(colorMode);
        } else {
            newAttributesBuilder.setColorMode(DEFAULT_COLOR_MODE);
        }

        if (duplexMode != null) {
            additionalAttributesBuilder.setDuplexMode(duplexMode);
            newAttributesBuilder.setDuplexMode(duplexMode);
        } else {
            newAttributesBuilder.setDuplexMode(DEFAULT_DUPLEX_MODE);
        }

        if (resolution != null) {
            additionalAttributesBuilder.setResolution(resolution);
            newAttributesBuilder.setResolution(resolution);
        } else {
            newAttributesBuilder.setResolution(DEFAULT_RESOLUTION);
        }

        CustomPrintOptionsActivity.setCallBack(
                (printJob, printer) -> {
                    PrintJobInfo.Builder printJobBuilder;

                    if (copyFromOriginal) {
                        printJobBuilder = new PrintJobInfo.Builder(printJob);
                    } else {
                        printJobBuilder = new PrintJobInfo.Builder(null);
                    }

                    if (numCopies != null) {
                        printJobBuilder.setCopies(numCopies);
                    }

                    if (pages != null) {
                        printJobBuilder.setPages(pages);
                    }

                    if (mediaSize != null || colorMode != null || duplexMode != null
                            || resolution != null) {
                        printJobBuilder.setAttributes(additionalAttributesBuilder.build());
                    }

                    return printJobBuilder.build();
                });

        // Check that the attributes were send to the print service
        PrintAttributes newAttributes = newAttributesBuilder.build();
        Log.i(LOG_TAG, "Change to attributes: " + newAttributes + ", copies: " + numCopies +
                ", pages: " + Arrays.toString(pages) + ", copyFromOriginal: " + copyFromOriginal);

        // This is the first print test that runs. If this is run after other tests these other test
        // can still cause memory pressure and make the printactivity to go through a destroy-create
        // cycle. In this case we have to retry to operation.
        int tries = 0;
        while (true) {
            try {
                resetCounters();

                // Start printing
                print(mAdapter);

                // Wait for write.
                waitForWriteAdapterCallback(1);

                // Open the print options.
                openPrintOptions();

                // Apply options by executing callback above
                Log.d(LOG_TAG, "Apply changes");
                openCustomPrintOptions();

                Log.d(LOG_TAG, "Check attributes");
                long endTime = System.currentTimeMillis() + OPERATION_TIMEOUT_MILLIS;
                synchronized (this) {
                    while (mLayoutAttributes == null ||
                            !mLayoutAttributes.equals(newAttributes)) {
                        wait(Math.max(1, endTime - System.currentTimeMillis()));

                        if (endTime < System.currentTimeMillis()) {
                            throw new TimeoutException(
                                    "Print attributes did not change to " + newAttributes + " in " +
                                            OPERATION_TIMEOUT_MILLIS + " ms. Current attributes"
                                            + mLayoutAttributes);
                        }
                    }
                }

                PageRange[] newPages;

                if (pages == null) {
                    newPages = new PageRange[] { new PageRange(0, 2) };
                } else {
                    newPages = pages;
                }

                Log.d(LOG_TAG, "Check pages");
                eventually(() -> {
                    PageRange[] actualPages = getPages();
                    if (!Arrays.equals(newPages, actualPages)) {
                        throw new AssertionError(
                                "Expected " + Arrays.toString(newPages) + ", actual " +
                                        Arrays.toString(actualPages));
                    }
                });

                break;
            } catch (Throwable e) {
                if (getActivityDestroyCallbackCallCount() > 0 && tries < MAX_TRIES) {
                    Log.e(LOG_TAG, "Activity was destroyed during test, retrying", e);

                    tries++;
                    continue;
                }

                throw e;
            }
        }

        // Abort printing
        getUiDevice().pressBack();
        getUiDevice().pressBack();
        getUiDevice().pressBack();

        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    @Test
    public void changeToChangeEveryThingButPages() throws Throwable {
        testCase(false, 2, null, MEDIA_SIZES[1], false, COLOR_MODES[1], DUPLEX_MODES[1],
                RESOLUTIONS[1]);
    }

    @Test
    public void changeToAttributes() throws Throwable {
        testCase(false, null, null, MEDIA_SIZES[1], false, COLOR_MODES[1], DUPLEX_MODES[1],
                RESOLUTIONS[1]);
    }

    @Test
    public void changeToNonAttributes() throws Throwable {
        testCase(false, 2, PAGESS[1], null, true, null, null, null);
    }

    @Test
    public void changeToAttributesNoCopy() throws Throwable {
        testCase(true, null, null, MEDIA_SIZES[1], false, COLOR_MODES[1], DUPLEX_MODES[1],
                RESOLUTIONS[1]);
    }

    @Test
    public void changeToNonAttributesNoCopy() throws Throwable {
        testCase(true, 2, PAGESS[1], null, true, null, null, null);
    }

    @Test
    public void changeToDefault() throws Throwable {
        testCase(false, 1, DEFAULT_PAGES, DEFAULT_MEDIA_SIZE, DEFAULT_MEDIA_SIZE.isPortrait(),
                DEFAULT_COLOR_MODE, DEFAULT_DUPLEX_MODE, DEFAULT_RESOLUTION);
    }

    @Test
    public void changeToDefaultNoCopy() throws Throwable {
        testCase(true, 1, DEFAULT_PAGES, DEFAULT_MEDIA_SIZE, DEFAULT_MEDIA_SIZE.isPortrait(),
                DEFAULT_COLOR_MODE, DEFAULT_DUPLEX_MODE, DEFAULT_RESOLUTION);
    }

    @Test
    public void changeToNothing() throws Throwable {
        testCase(false, null, null, null, true, null, null, null);
    }

    @Test
    public void testChangeToNothingNoCopy() throws Throwable {
        testCase(true, null, null, null, true, null, null, null);
    }

    @Test
    public void changeToAllPages() throws Throwable {
        testCase(false, null, PAGESS[2], null, true, null, null, null);
    }

    @Test
    public void changeToSomePages() throws Throwable {
        testCase(false, null, PAGESS[1], null, true, null, null, null);
    }
}
