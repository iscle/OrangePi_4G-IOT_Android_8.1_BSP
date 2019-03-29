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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.print.PrintAttributes;
import android.print.PrintAttributes.Margins;
import android.print.PrintAttributes.MediaSize;
import android.print.PrintAttributes.Resolution;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.print.cts.services.FirstPrintService;
import android.print.cts.services.InfoActivity;
import android.print.cts.services.PrintServiceCallbacks;
import android.print.cts.services.PrinterDiscoverySessionCallbacks;
import android.print.cts.services.SecondPrintService;
import android.print.cts.services.StubbablePrintService;
import android.print.cts.services.StubbablePrinterDiscoverySession;
import android.printservice.CustomPrinterIconCallback;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiSelector;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static android.print.cts.Utils.*;
import static org.junit.Assert.*;

/**
 * Test the interface from a print service to the print manager
 */
@RunWith(AndroidJUnit4.class)
public class PrintServicesTest extends BasePrintTest {
    private static final String PRINTER_NAME = "Test printer";

    /** The print job processed in the test */
    private static PrintJob sPrintJob;

    /** Printer under test */
    private static PrinterInfo sPrinter;

    /** The custom printer icon to use */
    private Icon mIcon;

    /**
     * Create a mock {@link PrinterDiscoverySessionCallbacks} that discovers a single printer with
     * minimal capabilities.
     *
     * @return The mock session callbacks
     */
    private PrinterDiscoverySessionCallbacks createMockPrinterDiscoverySessionCallbacks(
            String printerName, ArrayList<String> trackedPrinters) {
        return createMockPrinterDiscoverySessionCallbacks(invocation -> {
            // Get the session.
            StubbablePrinterDiscoverySession session =
                    ((PrinterDiscoverySessionCallbacks) invocation.getMock()).getSession();

            if (session.getPrinters().isEmpty()) {
                List<PrinterInfo> printers = new ArrayList<>();

                // Add the printer.
                PrinterId printerId = session.getService()
                        .generatePrinterId(printerName);

                PrinterCapabilitiesInfo capabilities = new PrinterCapabilitiesInfo.Builder(
                        printerId)
                        .setMinMargins(new Margins(200, 200, 200, 200))
                        .addMediaSize(MediaSize.ISO_A4, true)
                        .addResolution(new Resolution("300x300", "300x300", 300, 300),
                                true)
                        .setColorModes(PrintAttributes.COLOR_MODE_COLOR,
                                PrintAttributes.COLOR_MODE_COLOR)
                        .build();

                Intent infoIntent = new Intent(getActivity(), InfoActivity.class);
                infoIntent.putExtra("PRINTER_NAME", PRINTER_NAME);

                PendingIntent infoPendingIntent = PendingIntent.getActivity(getActivity(), 0,
                        infoIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                sPrinter = new PrinterInfo.Builder(printerId, printerName,
                        PrinterInfo.STATUS_IDLE)
                        .setCapabilities(capabilities)
                        .setDescription("Minimal capabilities")
                        .setInfoIntent(infoPendingIntent)
                        .build();
                printers.add(sPrinter);

                session.addPrinters(printers);
            }

            onPrinterDiscoverySessionCreateCalled();

            return null;
        }, null, null, invocation -> {
            if (trackedPrinters != null) {
                synchronized (trackedPrinters) {
                    trackedPrinters
                            .add(((PrinterId) invocation.getArguments()[0]).getLocalId());
                    trackedPrinters.notifyAll();
                }
            }
            return null;
        }, invocation -> {
            CustomPrinterIconCallback callback = (CustomPrinterIconCallback) invocation
                    .getArguments()[2];

            if (mIcon != null) {
                callback.onCustomPrinterIconLoaded(mIcon);
            }
            return null;
        }, invocation -> {
            if (trackedPrinters != null) {
                synchronized (trackedPrinters) {
                    trackedPrinters.remove(((PrinterId) invocation.getArguments()[0]).getLocalId());
                    trackedPrinters.notifyAll();
                }
            }

            return null;
        }, invocation -> {
            // Take a note onDestroy was called.
            onPrinterDiscoverySessionDestroyCalled();
            return null;
        });
    }

    /**
     * Get the current progress of #sPrintJob
     *
     * @return The current progress
     *
     * @throws InterruptedException If the thread was interrupted while setting the progress
     * @throws Throwable            If anything is unexpected.
     */
    private float getProgress() throws Throwable {
        float[] printProgress = new float[1];
        runOnMainThread(() -> printProgress[0] = sPrintJob.getInfo().getProgress());

        return printProgress[0];
    }

    /**
     * Get the current status of #sPrintJob
     *
     * @return The current status
     *
     * @throws InterruptedException If the thread was interrupted while getting the status
     * @throws Throwable            If anything is unexpected.
     */
    private CharSequence getStatus() throws Throwable {
        CharSequence[] printStatus = new CharSequence[1];
        runOnMainThread(() -> printStatus[0] = sPrintJob.getInfo().getStatus(getActivity()
                .getPackageManager()));

        return printStatus[0];
    }

    /**
     * Check if a print progress is correct.
     *
     * @param desiredProgress The expected @{link PrintProgresses}
     *
     * @throws Throwable If anything goes wrong or this takes more than 5 seconds
     */
    private void checkNotification(float desiredProgress, CharSequence desiredStatus)
            throws Throwable {
        eventually(() -> assertEquals(desiredProgress, getProgress(), 0.1));
        eventually(() -> assertEquals(desiredStatus.toString(), getStatus().toString()));
    }

    /**
     * Set a new progress and status for #sPrintJob
     *
     * @param progress The new progress to set
     * @param status   The new status to set
     *
     * @throws InterruptedException If the thread was interrupted while setting
     * @throws Throwable            If anything is unexpected.
     */
    private void setProgressAndStatus(final float progress, final CharSequence status)
            throws Throwable {
        runOnMainThread(() -> {
            sPrintJob.setProgress(progress);
            sPrintJob.setStatus(status);
        });
    }

    /**
     * Progress print job and check the print job state.
     *
     * @param progress How much to progress
     * @param status   The status to set
     *
     * @throws Throwable If anything goes wrong.
     */
    private void progress(float progress, CharSequence status) throws Throwable {
        setProgressAndStatus(progress, status);

        // Check that progress of job is correct
        checkNotification(progress, status);
    }

    /**
     * Create mock service callback for a session.
     *
     * @param sessionCallbacks The callbacks of the sessopm
     */
    private PrintServiceCallbacks createMockPrinterServiceCallbacks(
            final PrinterDiscoverySessionCallbacks sessionCallbacks) {
        return createMockPrintServiceCallbacks(
                invocation -> sessionCallbacks,
                invocation -> {
                    sPrintJob = (PrintJob) invocation.getArguments()[0];
                    sPrintJob.start();
                    onPrintJobQueuedCalled();

                    return null;
                }, invocation -> {
                    sPrintJob = (PrintJob) invocation.getArguments()[0];
                    sPrintJob.cancel();

                    return null;
                });
    }

    /**
     * Test that the progress and status is propagated correctly.
     *
     * @throws Throwable If anything is unexpected.
     */
    @Test
    public void progress() throws Throwable {
        // Create the session callbacks that we will be checking.
        PrinterDiscoverySessionCallbacks sessionCallbacks
                = createMockPrinterDiscoverySessionCallbacks(PRINTER_NAME, null);

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createMockPrinterServiceCallbacks(
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

        // Select the printer.
        selectPrinter(PRINTER_NAME);

        // Click the print button.
        clickPrintButton();

        // Answer the dialog for the print service cloud warning
        answerPrintServicesWarning(true);

        // Wait until the print job is queued and #sPrintJob is set
        waitForServiceOnPrintJobQueuedCallbackCalled(1);

        // Progress print job and check for appropriate notifications
        progress(0, "printed 0");
        progress(0.5f, "printed 50");
        progress(1, "printed 100");

        // Call complete from the main thread
        runOnMainThread(sPrintJob::complete);

        // Wait for all print jobs to be handled after which the session destroyed.
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    /**
     * Render a {@link Drawable} into a {@link Bitmap}.
     *
     * @param d the drawable to be rendered
     *
     * @return the rendered bitmap
     */
    private static Bitmap renderDrawable(Drawable d) {
        Bitmap bitmap = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        d.draw(canvas);

        return bitmap;
    }

    /**
     * Update the printer
     *
     * @param sessionCallbacks The callbacks for the service the printer belongs to
     * @param printer the new printer to use
     *
     * @throws InterruptedException If we were interrupted while the printer was updated.
     * @throws Throwable            If anything is unexpected.
     */
    private void updatePrinter(PrinterDiscoverySessionCallbacks sessionCallbacks,
            final PrinterInfo printer) throws Throwable {
        runOnMainThread(() -> {
            ArrayList<PrinterInfo> printers = new ArrayList<>(1);
            printers.add(printer);
            sessionCallbacks.getSession().addPrinters(printers);
        });

        // Update local copy of printer
        sPrinter = printer;
    }

    /**
     * Assert is the printer icon does not match the bitmap. As the icon update might take some time
     * we try up to 5 seconds.
     *
     * @param bitmap The bitmap to match
     *
     * @throws Throwable If anything is unexpected.
     */
    private void assertThatIconIs(Bitmap bitmap) throws Throwable {
        eventually(
                () -> assertTrue(bitmap.sameAs(renderDrawable(sPrinter.loadIcon(getActivity())))));
    }

    /**
     * Test that the icon get be updated.
     *
     * @throws Throwable If anything is unexpected.
     */
    @Test
    public void updateIcon() throws Throwable {
        // Create the session callbacks that we will be checking.
        final PrinterDiscoverySessionCallbacks sessionCallbacks
                = createMockPrinterDiscoverySessionCallbacks(PRINTER_NAME, null);

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createMockPrinterServiceCallbacks(
                sessionCallbacks);

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // We don't use the second service, but we have to still configure it
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

        // Start printing.
        print(adapter);

        // Open printer selection dropdown list to display icon on screen
        UiObject destinationSpinner = UiDevice.getInstance(getInstrumentation())
                .findObject(new UiSelector().resourceId(
                        "com.android.printspooler:id/destination_spinner"));
        destinationSpinner.click();

        // Get the print service's icon
        PackageManager packageManager = getActivity().getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageInfo(
                new ComponentName(getActivity(), FirstPrintService.class).getPackageName(), 0);
        ApplicationInfo appInfo = packageInfo.applicationInfo;
        Drawable printServiceIcon = appInfo.loadIcon(packageManager);

        assertThatIconIs(renderDrawable(printServiceIcon));

        // Update icon to resource
        updatePrinter(sessionCallbacks,
                (new PrinterInfo.Builder(sPrinter)).setIconResourceId(R.drawable.red_printer)
                .build());

        assertThatIconIs(renderDrawable(getActivity().getDrawable(R.drawable.red_printer)));

        // Update icon to bitmap
        Bitmap bm = BitmapFactory.decodeResource(getActivity().getResources(),
                R.raw.yellow);
        // Icon will be picked up from the discovery session once setHasCustomPrinterIcon is set
        mIcon = Icon.createWithBitmap(bm);
        updatePrinter(sessionCallbacks,
                (new PrinterInfo.Builder(sPrinter)).setHasCustomPrinterIcon(true).build());

        assertThatIconIs(renderDrawable(mIcon.loadDrawable(getActivity())));

        getUiDevice().pressBack();
        getUiDevice().pressBack();
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    /**
     * Test that we cannot call attachBaseContext
     *
     * @throws Throwable If anything is unexpected.
     */
    @Test
    public void cannotUseAttachBaseContext() throws Throwable {
        // Create the session callbacks that we will be checking.
        final PrinterDiscoverySessionCallbacks sessionCallbacks
                = createMockPrinterDiscoverySessionCallbacks(PRINTER_NAME, null);

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createMockPrinterServiceCallbacks(
                sessionCallbacks);

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

        // We don't use the second service, but we have to still configure it
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        // Start printing to set serviceCallbacks.getService()
        print(adapter);
        eventually(() -> assertNotNull(serviceCallbacks.getService()));

        // attachBaseContext should always throw an exception no matter what input value
        assertException(() -> serviceCallbacks.getService().callAttachBaseContext(null),
                IllegalStateException.class);
        assertException(() -> serviceCallbacks.getService().callAttachBaseContext(getActivity()),
                IllegalStateException.class);

        getUiDevice().pressBack();
        getUiDevice().pressBack();
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }

    /**
     * Test that the active print jobs can be read
     *
     * @throws Throwable If anything is unexpected.
     */
    @Test
    public void getActivePrintJobs() throws Throwable {
        clearPrintSpoolerData();

        try {
            PrintManager pm = (PrintManager) getActivity().getSystemService(Context.PRINT_SERVICE);

            // Configure first print service
            PrinterDiscoverySessionCallbacks sessionCallbacks1
                    = createMockPrinterDiscoverySessionCallbacks("Printer1", null);
            PrintServiceCallbacks serviceCallbacks1 = createMockPrinterServiceCallbacks(
                    sessionCallbacks1);
            FirstPrintService.setCallbacks(serviceCallbacks1);

            // Configure second print service
            PrinterDiscoverySessionCallbacks sessionCallbacks2
                    = createMockPrinterDiscoverySessionCallbacks("Printer2", null);
            PrintServiceCallbacks serviceCallbacks2 = createMockPrinterServiceCallbacks(
                    sessionCallbacks2);
            SecondPrintService.setCallbacks(serviceCallbacks2);

            // Create a print adapter that respects the print contract.
            PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

            runOnMainThread(() -> pm.print("job1", adapter, null));

            // Init services
            waitForPrinterDiscoverySessionCreateCallbackCalled();
            StubbablePrintService firstService = serviceCallbacks1.getService();

            waitForWriteAdapterCallback(1);
            selectPrinter("Printer1");

            // Job is not yet confirmed, hence it is not yet "active"
            runOnMainThread(() -> assertEquals(0, firstService.callGetActivePrintJobs().size()));

            clickPrintButton();
            answerPrintServicesWarning(true);
            onPrintJobQueuedCalled();

            eventually(() -> runOnMainThread(
                    () -> assertEquals(1, firstService.callGetActivePrintJobs().size())));

            // Add another print job to first service
            resetCounters();
            runOnMainThread(() -> pm.print("job2", adapter, null));
            waitForWriteAdapterCallback(1);
            clickPrintButton();
            onPrintJobQueuedCalled();

            eventually(() -> runOnMainThread(
                    () -> assertEquals(2, firstService.callGetActivePrintJobs().size())));

            // Create print job in second service
            resetCounters();
            runOnMainThread(() -> pm.print("job3", adapter, null));

            waitForPrinterDiscoverySessionCreateCallbackCalled();

            StubbablePrintService secondService = serviceCallbacks2.getService();
            runOnMainThread(() -> assertEquals(0, secondService.callGetActivePrintJobs().size()));

            waitForWriteAdapterCallback(1);
            selectPrinter("Printer2");
            clickPrintButton();
            answerPrintServicesWarning(true);
            onPrintJobQueuedCalled();

            eventually(() -> runOnMainThread(
                    () -> assertEquals(1, secondService.callGetActivePrintJobs().size())));
            runOnMainThread(() -> assertEquals(2, firstService.callGetActivePrintJobs().size()));

            // Block last print job. Blocked jobs are still considered active
            runOnMainThread(() -> sPrintJob.block(null));
            eventually(() -> runOnMainThread(() -> assertTrue(sPrintJob.isBlocked())));
            runOnMainThread(() -> assertEquals(1, secondService.callGetActivePrintJobs().size()));

            // Fail last print job. Failed job are not active
            runOnMainThread(() -> sPrintJob.fail(null));
            eventually(() -> runOnMainThread(() -> assertTrue(sPrintJob.isFailed())));
            runOnMainThread(() -> assertEquals(0, secondService.callGetActivePrintJobs().size()));

            // Cancel job. Canceled jobs are not active
            runOnMainThread(() -> assertEquals(2, firstService.callGetActivePrintJobs().size()));
            android.print.PrintJob job2 = getPrintJob(pm, "job2");
            runOnMainThread(job2::cancel);
            eventually(() -> runOnMainThread(() -> assertTrue(job2.isCancelled())));
            runOnMainThread(() -> assertEquals(1, firstService.callGetActivePrintJobs().size()));

            waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
        } finally {
            clearPrintSpoolerData();
        }
    }

    /**
     * Test that the icon get be updated.
     *
     * @throws Throwable If anything is unexpected.
     */
    @Test
    public void selectViaInfoIntent() throws Throwable {
        ArrayList<String> trackedPrinters = new ArrayList<>();

        // Create the session callbacks that we will be checking.
        final PrinterDiscoverySessionCallbacks sessionCallbacks
                = createMockPrinterDiscoverySessionCallbacks(PRINTER_NAME, trackedPrinters);

        // Create the service callbacks for the first print service.
        PrintServiceCallbacks serviceCallbacks = createMockPrinterServiceCallbacks(
                sessionCallbacks);

        // Configure the print services.
        FirstPrintService.setCallbacks(serviceCallbacks);

        // We don't use the second service, but we have to still configure it
        SecondPrintService.setCallbacks(createMockPrintServiceCallbacks(null, null, null));

        // Create a print adapter that respects the print contract.
        PrintDocumentAdapter adapter = createDefaultPrintDocumentAdapter(1);

        // Start printing.
        print(adapter);

        // Enter select printer activity
        selectPrinter("All printers…");

        assertFalse(trackedPrinters.contains(PRINTER_NAME));

        InfoActivity.addObserver(activity -> {
            Intent intent = activity.getIntent();

            assertEquals(PRINTER_NAME, intent.getStringExtra("PRINTER_NAME"));
            assertTrue(intent.getBooleanExtra(PrintService.EXTRA_CAN_SELECT_PRINTER,
                            false));

            activity.setResult(Activity.RESULT_OK,
                    (new Intent()).putExtra(PrintService.EXTRA_SELECT_PRINTER, true));
            activity.finish();
        });

        // Open info activity which executed the code above
        UiObject moreInfoButton = getUiDevice().findObject(new UiSelector().resourceId(
                "com.android.printspooler:id/more_info"));
        moreInfoButton.click();

        // Wait until printer is selected and thereby tracked
        eventually(() -> assertTrue(trackedPrinters.contains(PRINTER_NAME)));

        InfoActivity.clearObservers();

        getUiDevice().pressBack();
        getUiDevice().pressBack();
        waitForPrinterDiscoverySessionDestroyCallbackCalled(1);
    }
}
