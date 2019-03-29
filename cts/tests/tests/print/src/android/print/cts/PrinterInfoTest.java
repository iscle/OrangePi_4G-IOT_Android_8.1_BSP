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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.print.PrintAttributes;
import android.print.PrintAttributes.Margins;
import android.print.PrintAttributes.MediaSize;
import android.print.PrintAttributes.Resolution;
import android.print.PrintDocumentAdapter;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.cts.services.FirstPrintService;
import android.print.cts.services.PrintServiceCallbacks;
import android.print.cts.services.PrinterDiscoverySessionCallbacks;
import android.print.cts.services.SecondPrintService;
import android.print.cts.services.StubbablePrinterDiscoverySession;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;
import android.text.TextUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests all allowed types of printerInfo
 */
@RunWith(AndroidJUnit4.class)
public class PrinterInfoTest extends BasePrintTest {
    private static final String NAMED_PRINTERS_NAME_PREFIX = "Printer ";

    /** The printer discovery session used in this test */
    private static StubbablePrinterDiscoverySession sDiscoverySession;

    private boolean isValidStatus(int status) {
        return status == PrinterInfo.STATUS_IDLE
                || status == PrinterInfo.STATUS_BUSY
                || status == PrinterInfo.STATUS_UNAVAILABLE;
    }

    /**
     * Create a mock {@link PrinterDiscoverySessionCallbacks} that discovers a printers with all
     * possible combinations of interesting printers.
     *
     * @return The mock session callbacks
     */
    private PrinterDiscoverySessionCallbacks createFirstMockPrinterDiscoverySessionCallbacks() {
        return createMockPrinterDiscoverySessionCallbacks(invocation -> {
            // Get the session.
            sDiscoverySession = ((PrinterDiscoverySessionCallbacks) invocation.getMock())
                    .getSession();

            if (sDiscoverySession.getPrinters().isEmpty()) {
                final int INVALID_RES_ID = 0xffffffff;

                final PrinterInfo.Builder badPrinter = new PrinterInfo.Builder(
                        sDiscoverySession.getService().generatePrinterId("bad printer"),
                        "badPrinter", PrinterInfo.STATUS_UNAVAILABLE);

                String[] localPrinterIds = {
                        "Printer", null
                };

                String[] names = {
                        NAMED_PRINTERS_NAME_PREFIX, "", null
                };
                int[] statusList = {
                        PrinterInfo.STATUS_IDLE, PrinterInfo.STATUS_BUSY,
                        PrinterInfo.STATUS_UNAVAILABLE, 0, 42
                };
                int[] iconResourceIds = {
                        R.drawable.red_printer, 0, INVALID_RES_ID, -1
                };

                boolean[] hasCustomPrinterIcons = {
                        true, false
                };

                String[] descriptions = {
                        "Description", "", null
                };

                PendingIntent[] infoIntents = {
                        PendingIntent.getActivity(getActivity(), 0,
                                new Intent(getActivity(), Activity.class),
                                PendingIntent.FLAG_IMMUTABLE),
                        null
                };

                PrinterCapabilitiesInfo[] capabilityList = {
                        // The printerId not used in PrinterCapabilitiesInfo
                        new PrinterCapabilitiesInfo.Builder(sDiscoverySession.getService()
                                .generatePrinterId("unused"))
                                        .setMinMargins(new Margins(200, 200, 200, 200))
                                        .addMediaSize(MediaSize.ISO_A4, true)
                                        .addResolution(
                                                new Resolution("300x300", "300x300", 300, 300),
                                                true)
                                        .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                                PrintAttributes.COLOR_MODE_COLOR)
                                        .build(),
                        null
                };

                List<PrinterInfo> printers = new ArrayList<>();
                for (String localPrinterId : localPrinterIds) {
                    for (String name : names) {
                        for (int status : statusList) {
                            for (int iconResourceId : iconResourceIds) {
                                for (boolean hasCustomPrinterIcon : hasCustomPrinterIcons) {
                                    for (String description : descriptions) {
                                        for (PendingIntent infoIntent : infoIntents) {
                                            for (PrinterCapabilitiesInfo capabilities
                                                    : capabilityList) {
                                                // printerId
                                                RuntimeException e = null;
                                                PrinterId printerId = null;
                                                try {
                                                    if (localPrinterId == null) {
                                                        printerId = sDiscoverySession
                                                                .getService()
                                                                .generatePrinterId(
                                                                        localPrinterId);
                                                    } else {
                                                        printerId = sDiscoverySession
                                                                .getService()
                                                                .generatePrinterId(
                                                                        localPrinterId
                                                                                + printers
                                                                                        .size());
                                                    }
                                                } catch (RuntimeException ex) {
                                                    e = ex;
                                                }

                                                // Expect exception if localId is null
                                                if (localPrinterId == null) {
                                                    if (e == null) {
                                                        throw new IllegalStateException();
                                                    }
                                                } else if (e != null) {
                                                    throw e;
                                                }

                                                // Constructor
                                                PrinterInfo.Builder b = null;
                                                e = null;
                                                try {
                                                    b = new PrinterInfo.Builder(
                                                            printerId, name, status);
                                                } catch (RuntimeException ex) {
                                                    e = ex;
                                                }

                                                // Expect exception if any of the parameters was
                                                // bad
                                                if (printerId == null
                                                        || TextUtils.isEmpty(name)
                                                        || !isValidStatus(status)) {
                                                    if (e == null) {
                                                        throw new IllegalStateException();
                                                    } else {
                                                        b = badPrinter;
                                                    }
                                                } else if (e != null) {
                                                    throw e;
                                                }

                                                // IconResourceId
                                                e = null;
                                                try {
                                                    b.setIconResourceId(iconResourceId);
                                                } catch (RuntimeException ex) {
                                                    e = ex;
                                                }

                                                // Expect exception if iconResourceId was bad
                                                // We do allow invalid Ids as the printerInfo
                                                // might be created after the package of the
                                                // printer is already gone if the printer is a
                                                // historical printer.
                                                if (iconResourceId < 0) {
                                                    if (e == null) {
                                                        throw new IllegalStateException();
                                                    } else {
                                                        b = badPrinter;
                                                    }
                                                } else if (e != null) {
                                                    throw e;
                                                }

                                                // Status
                                                e = null;
                                                try {
                                                    b.setStatus(status);
                                                } catch (RuntimeException ex) {
                                                    e = ex;
                                                }

                                                // Expect exception is status is not valid
                                                if (!isValidStatus(status)) {
                                                    if (e == null) {
                                                        throw new IllegalStateException();
                                                    } else {
                                                        b = badPrinter;
                                                    }
                                                } else if (e != null) {
                                                    throw e;
                                                }

                                                // Name
                                                e = null;
                                                try {
                                                    b.setName(name);
                                                } catch (RuntimeException ex) {
                                                    e = ex;
                                                }

                                                // Expect exception if name is empty
                                                if (TextUtils.isEmpty(name)) {
                                                    if (e == null) {
                                                        throw new IllegalStateException();
                                                    } else {
                                                        b = badPrinter;
                                                    }
                                                } else if (e != null) {
                                                    throw e;
                                                }

                                                // hasCustomPrinterIcon
                                                b.setHasCustomPrinterIcon(hasCustomPrinterIcon);

                                                // Description
                                                b.setDescription(description);

                                                // InfoIntent
                                                b.setInfoIntent(infoIntent);

                                                // Capabilities
                                                b.setCapabilities(capabilities);

                                                PrinterInfo printer = b.build();

                                                // Don't create bad printers
                                                if (b == badPrinter) {
                                                    continue;
                                                }

                                                // Verify get* methods
                                                if (printer.getId() != printerId
                                                        || printer.getName() != name
                                                        || printer.getStatus() != status
                                                        || printer
                                                                .getDescription() != description
                                                        || printer.getCapabilities()
                                                                != capabilities) {
                                                    throw new IllegalStateException();
                                                }

                                                printers.add(printer);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                sDiscoverySession.addPrinters(printers);
            }
            return null;
        }, null, null, invocation -> null, invocation -> null, null, invocation -> {
            // Take a note onDestroy was called.
            onPrinterDiscoverySessionDestroyCalled();
            return null;
        });
    }

    /**
     * Create mock service callback for a session.
     *
     * @param sessionCallbacks The callbacks of the session
     */
    private PrintServiceCallbacks createFirstMockPrinterServiceCallbacks(
            final PrinterDiscoverySessionCallbacks sessionCallbacks) {
        return createMockPrintServiceCallbacks(
                invocation -> sessionCallbacks,
                null, null);
    }

    /**
     * Test that all printInfos possible can be used and that invalid printInfos throw exceptions.
     *
     * @throws Exception If anything is unexpected.
     */
    @Test
    public void printerInfos() throws Exception {
        // Create the session of the printers that we will be checking.
        PrinterDiscoverySessionCallbacks sessionCallbacks
                = createFirstMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createFirstMockPrinterServiceCallbacks(
                sessionCallbacks);

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // We don't use the second service, but we have to still configure it
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

        // Start printing.
        print(adapter);

        // Wait for write of the first page.
        waitForWriteAdapterCallback(1);

        // Open destination spinner
        UiObject destinationSpinner = getUiDevice().findObject(new UiSelector().resourceId(
                "com.android.printspooler:id/destination_spinner"));
        destinationSpinner.click();

        // Wait until spinner is opened
        getUiDevice().waitForIdle();

        // Exit print spooler
        getUiDevice().pressBack();
        getUiDevice().pressBack();
        getUiDevice().pressBack();
    }
}
