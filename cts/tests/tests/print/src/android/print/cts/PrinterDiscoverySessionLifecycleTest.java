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

import static android.print.cts.Utils.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.inOrder;

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
import android.printservice.PrintJob;
import android.printservice.PrinterDiscoverySession;

import android.support.test.runner.AndroidJUnit4;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.exceptions.verification.VerificationInOrderFailure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This test verifies that the system respects the {@link PrinterDiscoverySession}
 * contract is respected.
 */
@RunWith(AndroidJUnit4.class)
public class PrinterDiscoverySessionLifecycleTest extends BasePrintTest {
    private static final String FIRST_PRINTER_NAME = "First printer";
    private static final String SECOND_PRINTER_NAME = "Second printer";

    private static final String FIRST_PRINTER_LOCAL_ID= "first_printer";
    private static final String SECOND_PRINTER_LOCAL_ID = "second_printer";

    private static StubbablePrinterDiscoverySession sSession;

    @Before
    public void clearPrintSpoolerState() throws Exception {
        clearPrintSpoolerData();
    }

    @Test
    public void normalLifecycle() throws Throwable {
        // Create the session callbacks that we will be checking.
        final PrinterDiscoverySessionCallbacks firstSessionCallbacks =
                createFirstMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks firstServiceCallbacks = createMockPrintServiceCallbacks(
                invocation -> firstSessionCallbacks,
                invocation -> {
                    PrintJob printJob = (PrintJob) invocation.getArguments()[0];
                    // We pretend the job is handled immediately.
                    printJob.complete();
                    return null;
                }, null);

        // Configure the print services.
        FirstPrintService.setCallbacks(firstServiceCallbacks);
        SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

        // Start printing.
        print(adapter);

        // Wait for write of the first page.
        waitForWriteAdapterCallback(1);

        runOnMainThread(() -> assertFalse(sSession.isDestroyed()));
        runOnMainThread(() -> assertEquals(0, sSession.getTrackedPrinters().size()));

        // Select the first printer.
        selectPrinter(FIRST_PRINTER_NAME);

        eventually(() -> runOnMainThread(() -> assertEquals(FIRST_PRINTER_LOCAL_ID,
                sSession.getTrackedPrinters().get(0).getLocalId())));
        runOnMainThread(() -> assertTrue(sSession.isPrinterDiscoveryStarted()));
        runOnMainThread(() -> assertEquals(1, sSession.getTrackedPrinters().size()));

        // Wait for layout as the printer has different capabilities.
        waitForLayoutAdapterCallbackCount(2);

        // Select the second printer (same capabilities as the other
        // one so no layout should happen).
        selectPrinter(SECOND_PRINTER_NAME);

        eventually(() -> runOnMainThread(() -> assertEquals(SECOND_PRINTER_LOCAL_ID,
                sSession.getTrackedPrinters().get(0).getLocalId())));
        runOnMainThread(() -> assertEquals(1, sSession.getTrackedPrinters().size()));

        // While the printer discovery session is still alive store the
        // ids of printers as we want to make some assertions about them
        // but only the print service can create printer ids which means
        // that we need to get the created ones.
        PrinterId firstPrinterId = getAddedPrinterIdForLocalId(
                FIRST_PRINTER_LOCAL_ID);
        PrinterId secondPrinterId = getAddedPrinterIdForLocalId(
                SECOND_PRINTER_LOCAL_ID);
        assertNotNull("Coundn't find printer:" + FIRST_PRINTER_LOCAL_ID, firstPrinterId);
        assertNotNull("Coundn't find printer:" + SECOND_PRINTER_LOCAL_ID, secondPrinterId);

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for all print jobs to be handled after which the session destroyed.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        runOnMainThread(() -> assertTrue(sSession.isDestroyed()));
        runOnMainThread(() -> assertFalse(sSession.isPrinterDiscoveryStarted()));
        runOnMainThread(() -> assertEquals(0, sSession.getTrackedPrinters().size()));

        // Verify the expected calls.
        InOrder inOrder = inOrder(firstSessionCallbacks);

        // We start discovery as the print dialog was up.
        List<PrinterId> emptyPrinterIdList = Collections.emptyList();
        inOrder.verify(firstSessionCallbacks).onStartPrinterDiscovery(
                emptyPrinterIdList);

        // We selected the first printer and now it should be tracked.
        inOrder.verify(firstSessionCallbacks).onStartPrinterStateTracking(
                firstPrinterId);

        // We selected the second printer so the first should not be tracked.
        inOrder.verify(firstSessionCallbacks).onStopPrinterStateTracking(
                firstPrinterId);

        // We selected the second printer and now it should be tracked.
        inOrder.verify(firstSessionCallbacks).onStartPrinterStateTracking(
                secondPrinterId);

        // The print dialog went away so we first stop the printer tracking...
        inOrder.verify(firstSessionCallbacks).onStopPrinterStateTracking(
                secondPrinterId);

        // ... next we stop printer discovery...
        inOrder.verify(firstSessionCallbacks).onStopPrinterDiscovery();

        // ... last the session is destroyed.
        inOrder.verify(firstSessionCallbacks).onDestroy();
    }

    @Test
    public void cancelPrintServicesAlertDialog() throws Throwable {
        // Create the session callbacks that we will be checking.
        final PrinterDiscoverySessionCallbacks firstSessionCallbacks =
                createFirstMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks firstServiceCallbacks = createMockPrintServiceCallbacks(
                invocation -> firstSessionCallbacks,
                invocation -> {
                    PrintJob printJob = (PrintJob) invocation.getArguments()[0];
                    // We pretend the job is handled immediately.
                    printJob.complete();
                    return null;
                }, null);

        // Configure the print services.
        FirstPrintService.setCallbacks(firstServiceCallbacks);
        SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

        // Start printing.
        print(adapter);

        // Wait for write of the first page.
        waitForWriteAdapterCallback(1);

        runOnMainThread(() -> assertFalse(sSession.isDestroyed()));
        runOnMainThread(() -> assertEquals(0, sSession.getTrackedPrinters().size()));

        // Select the first printer.
        selectPrinter(FIRST_PRINTER_NAME);

        eventually(() -> runOnMainThread(() -> assertEquals(FIRST_PRINTER_LOCAL_ID,
                sSession.getTrackedPrinters().get(0).getLocalId())));
        runOnMainThread(() -> assertTrue(sSession.isPrinterDiscoveryStarted()));
        runOnMainThread(() -> assertEquals(1, sSession.getTrackedPrinters().size()));

        // While the printer discovery session is still alive store the
        // ids of printers as we want to make some assertions about them
        // but only the print service can create printer ids which means
        // that we need to get the created ones.
        PrinterId firstPrinterId = getAddedPrinterIdForLocalId(
                FIRST_PRINTER_LOCAL_ID);
        assertNotNull("Coundn't find printer:" + FIRST_PRINTER_LOCAL_ID, firstPrinterId);

        // Click the print button.
        clickPrintButton();

        // Cancel the dialog for the print service cloud warning
        answerPrintServicesWarning(false);

        // Click the print button again.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for all print jobs to be handled after which the session destroyed.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        runOnMainThread(() -> assertTrue(sSession.isDestroyed()));
        runOnMainThread(() -> assertFalse(sSession.isPrinterDiscoveryStarted()));
        runOnMainThread(() -> assertEquals(0, sSession.getTrackedPrinters().size()));

        // Verify the expected calls.
        InOrder inOrder = inOrder(firstSessionCallbacks);

        // We start discovery as the print dialog was up.
        List<PrinterId> emptyPrinterIdList = Collections.emptyList();
        inOrder.verify(firstSessionCallbacks).onStartPrinterDiscovery(
                emptyPrinterIdList);

        // We selected the first printer and now it should be tracked.
        inOrder.verify(firstSessionCallbacks).onStartPrinterStateTracking(
                firstPrinterId);

        // We selected the second printer so the first should not be tracked.
        inOrder.verify(firstSessionCallbacks).onStopPrinterStateTracking(
                firstPrinterId);

        // ... next we stop printer discovery...
        inOrder.verify(firstSessionCallbacks).onStopPrinterDiscovery();

        // ... last the session is destroyed.
        inOrder.verify(firstSessionCallbacks).onDestroy();
    }

    @Test
    public void startPrinterDiscoveryWithHistoricalPrinters() throws Throwable {
        // Create the session callbacks that we will be checking.
        final PrinterDiscoverySessionCallbacks firstSessionCallbacks =
                createFirstMockPrinterDiscoverySessionCallbacks();

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks firstServiceCallbacks = createMockPrintServiceCallbacks(
                invocation -> firstSessionCallbacks,
                invocation -> {
                    PrintJob printJob = (PrintJob) invocation.getArguments()[0];
                    // We pretend the job is handled immediately.
                    printJob.complete();
                    return null;
                }, null);

        // Configure the print services.
        FirstPrintService.setCallbacks(firstServiceCallbacks);
        SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

        // Start printing.
        print(adapter);

        // Wait for write of the first page.
        waitForWriteAdapterCallback(1);

        runOnMainThread(() -> assertFalse(sSession.isDestroyed()));
        runOnMainThread(() -> assertEquals(0, sSession.getTrackedPrinters().size()));

        // Select the first printer.
        selectPrinter(FIRST_PRINTER_NAME);

        eventually(() -> runOnMainThread(() -> assertEquals(FIRST_PRINTER_LOCAL_ID,
                sSession.getTrackedPrinters().get(0).getLocalId())));
        runOnMainThread(() -> assertTrue(sSession.isPrinterDiscoveryStarted()));
        runOnMainThread(() -> assertEquals(1, sSession.getTrackedPrinters().size()));

        // Wait for a layout to finish - first layout was for the
        // PDF printer, second for the first printer in preview mode.
        waitForLayoutAdapterCallbackCount(2);

        // While the printer discovery session is still alive store the
        // ids of printer as we want to make some assertions about it
        // but only the print service can create printer ids which means
        // that we need to get the created one.
        PrinterId firstPrinterId = getAddedPrinterIdForLocalId(
                FIRST_PRINTER_LOCAL_ID);

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait for the print to complete.
        waitForAdapterFinishCallbackCalled();

        // Now print again as we want to confirm that the start
        // printer discovery passes in the priority list.
        print(adapter);

        // Wait for a layout to finish - first layout was for the
        // PDF printer, second for the first printer in preview mode,
        // the third for the first printer in non-preview mode, and
        // now a fourth for the PDF printer as we are printing again.
        waitForLayoutAdapterCallbackCount(4);

        // Cancel the printing.
        getUiDevice().pressBack(); // wakes up the device.
        getUiDevice().pressBack();

        // Wait for all print jobs to be handled after which the is session destroyed.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);

        runOnMainThread(() -> assertTrue(sSession.isDestroyed()));
        runOnMainThread(() -> assertFalse(sSession.isPrinterDiscoveryStarted()));
        runOnMainThread(() -> assertEquals(0, sSession.getTrackedPrinters().size()));

        // Verify the expected calls.
        InOrder inOrder = inOrder(firstSessionCallbacks);

        // We start discovery with no printer history.
        List<PrinterId> priorityList = new ArrayList<>();
        inOrder.verify(firstSessionCallbacks).onStartPrinterDiscovery(
                priorityList);

        // We selected the first printer and now it should be tracked.
        inOrder.verify(firstSessionCallbacks).onStartPrinterStateTracking(
                firstPrinterId);

        // We confirmed print so the first should not be tracked.
        inOrder.verify(firstSessionCallbacks).onStopPrinterStateTracking(
                firstPrinterId);

        // This is tricky. It is possible that the print activity was not
        // destroyed (the platform delays destruction at convenient time as
        // an optimization) and we get the same instance which means that
        // the discovery session may not have been destroyed. We try the
        // case with the activity being destroyed and if this fails the
        // case with the activity brought to front.
        priorityList.add(firstPrinterId);
        try {
            inOrder.verify(firstSessionCallbacks).onStartPrinterDiscovery(priorityList);
        } catch (VerificationInOrderFailure error) {
            inOrder.verify(firstSessionCallbacks).onValidatePrinters(priorityList);
        }

        // The system selects the highest ranked historical printer.
        inOrder.verify(firstSessionCallbacks).onStartPrinterStateTracking(
                firstPrinterId);

        // We canceled print so the first should not be tracked.
        inOrder.verify(firstSessionCallbacks).onStopPrinterStateTracking(
                firstPrinterId);


        // Discovery is always stopped before the session is always destroyed.
        inOrder.verify(firstSessionCallbacks).onStopPrinterDiscovery();

        // ...last the session is destroyed.
        inOrder.verify(firstSessionCallbacks).onDestroy();
    }

    @Test
    public void addRemovePrinters() throws Throwable {
        StubbablePrinterDiscoverySession[] session = new StubbablePrinterDiscoverySession[1];

        // Create the session callbacks that we will be checking.
        final PrinterDiscoverySessionCallbacks firstSessionCallbacks =
                createMockPrinterDiscoverySessionCallbacks(invocation -> {
                    session[0] = ((PrinterDiscoverySessionCallbacks)
                            invocation.getMock()).getSession();

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
        SecondPrintService.setCallbacks(createSecondMockPrintServiceCallbacks());

        print(createDefaultPrintDocumentAdapter(1));

        waitForPrinterDiscoverySessionCreateCallbackCalled();

        runOnMainThread(() -> assertEquals(0, session[0].getPrinters().size()));

        PrinterId[] printerIds = new PrinterId[3];
        runOnMainThread(() -> {
            printerIds[0] = session[0].getService().generatePrinterId("0");
            printerIds[1] = session[0].getService().generatePrinterId("1");
            printerIds[2] = session[0].getService().generatePrinterId("2");
        });

        PrinterInfo printer1 = (new PrinterInfo.Builder(printerIds[0], "0",
                PrinterInfo.STATUS_IDLE)).build();

        PrinterInfo printer2 = (new PrinterInfo.Builder(printerIds[1], "1",
                PrinterInfo.STATUS_IDLE)).build();

        PrinterInfo printer3 = (new PrinterInfo.Builder(printerIds[2], "2",
                PrinterInfo.STATUS_IDLE)).build();

        ArrayList<PrinterInfo> printers = new ArrayList<>();
        printers.add(printer1);
        runOnMainThread(() -> session[0].addPrinters(printers));
        eventually(() -> runOnMainThread(() -> assertEquals(1, session[0].getPrinters().size())));

        printers.add(printer2);
        printers.add(printer3);
        runOnMainThread(() -> session[0].addPrinters(printers));
        eventually(() -> runOnMainThread(() -> assertEquals(3, session[0].getPrinters().size())));

        ArrayList<PrinterId> printerIdsToRemove = new ArrayList<>();
        printerIdsToRemove.add(printer1.getId());
        runOnMainThread(() -> session[0].removePrinters(printerIdsToRemove));
        eventually(() -> runOnMainThread(() -> assertEquals(2, session[0].getPrinters().size())));

        printerIdsToRemove.add(printer2.getId());
        printerIdsToRemove.add(printer3.getId());
        runOnMainThread(() -> session[0].removePrinters(printerIdsToRemove));
        eventually(() -> runOnMainThread(() -> assertEquals(0, session[0].getPrinters().size())));

        getUiDevice().pressBack();

        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    private PrinterId getAddedPrinterIdForLocalId(String printerLocalId) throws Throwable {
        final List<PrinterInfo> reportedPrinters = new ArrayList<>();
        runOnMainThread(() -> {
            // Grab the printer ids as only the service can create such.
            reportedPrinters.addAll(sSession.getPrinters());
        });

        final int reportedPrinterCount = reportedPrinters.size();
        for (int i = 0; i < reportedPrinterCount; i++) {
            PrinterInfo reportedPrinter = reportedPrinters.get(i);
            String localId = reportedPrinter.getId().getLocalId();
            if (printerLocalId.equals(localId)) {
                return reportedPrinter.getId();
            }
        }

        return null;
    }

    private PrintServiceCallbacks createSecondMockPrintServiceCallbacks() {
        return createMockPrintServiceCallbacks(null, null, null);
    }

    private PrinterDiscoverySessionCallbacks createFirstMockPrinterDiscoverySessionCallbacks() {
        return createMockPrinterDiscoverySessionCallbacks(invocation -> {
            // Get the session.
            sSession = ((PrinterDiscoverySessionCallbacks)
                    invocation.getMock()).getSession();

            assertTrue(sSession.isPrinterDiscoveryStarted());

            if (sSession.getPrinters().isEmpty()) {
                List<PrinterInfo> printers = new ArrayList<>();

                // Add the first printer.
                PrinterId firstPrinterId = sSession.getService().generatePrinterId(
                        FIRST_PRINTER_LOCAL_ID);
                PrinterInfo firstPrinter = new PrinterInfo.Builder(firstPrinterId,
                        FIRST_PRINTER_NAME, PrinterInfo.STATUS_IDLE)
                    .build();
                printers.add(firstPrinter);

                // Add the first printer.
                PrinterId secondPrinterId = sSession.getService().generatePrinterId(
                        SECOND_PRINTER_LOCAL_ID);
                PrinterInfo secondPrinter = new PrinterInfo.Builder(secondPrinterId,
                        SECOND_PRINTER_NAME, PrinterInfo.STATUS_IDLE)
                    .build();
                printers.add(secondPrinter);

                sSession.addPrinters(printers);
            }
            return null;
        }, invocation -> {
            assertFalse(sSession.isPrinterDiscoveryStarted());
            return null;
        }, null, invocation -> {
            // Get the session.
            StubbablePrinterDiscoverySession session = ((PrinterDiscoverySessionCallbacks)
                    invocation.getMock()).getSession();

            PrinterId trackedPrinterId = (PrinterId) invocation.getArguments()[0];
            List<PrinterInfo> reportedPrinters = session.getPrinters();

            // We should be tracking a printer that we added.
            PrinterInfo trackedPrinter = null;
            final int reportedPrinterCount = reportedPrinters.size();
            for (int i = 0; i < reportedPrinterCount; i++) {
                PrinterInfo reportedPrinter = reportedPrinters.get(i);
                if (reportedPrinter.getId().equals(trackedPrinterId)) {
                    trackedPrinter = reportedPrinter;
                    break;
                }
            }
            assertNotNull("Can track only added printers", trackedPrinter);

            assertTrue(sSession.getTrackedPrinters().contains(trackedPrinter.getId()));
            assertEquals(1, sSession.getTrackedPrinters().size());

            // If the printer does not have capabilities reported add them.
            if (trackedPrinter.getCapabilities() == null) {

                // Add the capabilities to emulate lazy discovery.
                // Same for each printer is fine for what we test.
                PrinterCapabilitiesInfo capabilities =
                        new PrinterCapabilitiesInfo.Builder(trackedPrinterId)
                                .setMinMargins(new Margins(200, 200, 200, 200))
                                .addMediaSize(MediaSize.ISO_A4, true)
                                .addMediaSize(MediaSize.ISO_A5, false)
                                .addResolution(new Resolution("300x300", "300x300", 300, 300), true)
                                .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                        PrintAttributes.COLOR_MODE_COLOR)
                                .build();
                PrinterInfo updatedPrinter = new PrinterInfo.Builder(trackedPrinter)
                        .setCapabilities(capabilities)
                        .build();

                // Update the printer.
                List<PrinterInfo> printers = new ArrayList<>();
                printers.add(updatedPrinter);
                session.addPrinters(printers);
            }

            return null;
        }, null, null, invocation -> {
            assertTrue(sSession.isDestroyed());

            // Take a note onDestroy was called.
            onPrinterDiscoverySessionDestroyCalled();
            return null;
        });
    }
}
