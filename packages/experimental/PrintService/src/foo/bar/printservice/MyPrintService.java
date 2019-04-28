/*
 * Copyright (C) 2013 The Android Open Source Project
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

package foo.bar.printservice;

import android.annotation.NonNull;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintAttributes.Margins;
import android.print.PrintAttributes.MediaSize;
import android.print.PrintAttributes.Resolution;
import android.print.PrintJobId;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.CustomPrinterIconCallback;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;
import android.util.ArrayMap;
import android.util.Log;
import com.android.internal.os.SomeArgs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MyPrintService extends PrintService {

    private static final String LOG_TAG = "MyPrintService";

    private static final long STANDARD_DELAY_MILLIS = 10000000;

    static final String INTENT_EXTRA_ACTION_TYPE = "INTENT_EXTRA_ACTION_TYPE";
    static final String INTENT_EXTRA_PRINT_JOB_ID = "INTENT_EXTRA_PRINT_JOB_ID";

    static final int ACTION_TYPE_ON_PRINT_JOB_PENDING = 1;
    private static final int ACTION_TYPE_ON_REQUEST_CANCEL_PRINT_JOB = 2;

    private static final Object sLock = new Object();

    private static MyPrintService sInstance;

    private Handler mHandler;

    private AsyncTask<ParcelFileDescriptor, Void, Void> mFakePrintTask;

    private final Map<PrintJobId, PrintJob> mProcessedPrintJobs =
            new ArrayMap<>();

    public static MyPrintService peekInstance() {
        synchronized (sLock) {
            return sInstance;
        }
    }

    @Override
    protected void onConnected() {
        Log.i(LOG_TAG, "#onConnected()");
        mHandler = new MyHandler(getMainLooper());
        synchronized (sLock) {
            sInstance = this;
        }
    }

    @Override
    protected void onDisconnected() {
        Log.i(LOG_TAG, "#onDisconnected()");
        synchronized (sLock) {
            sInstance = null;
        }
    }

    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        Log.i(LOG_TAG, "#onCreatePrinterDiscoverySession()");
        return new FakePrinterDiscoverySession();
    }

    @Override
    protected void onRequestCancelPrintJob(final PrintJob printJob) {
        Log.i(LOG_TAG, "#onRequestCancelPrintJob()");
        mProcessedPrintJobs.put(printJob.getId(), printJob);
        Intent intent = new Intent(this, MyDialogActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(INTENT_EXTRA_PRINT_JOB_ID, printJob.getId());
        intent.putExtra(INTENT_EXTRA_ACTION_TYPE, ACTION_TYPE_ON_REQUEST_CANCEL_PRINT_JOB);
        startActivity(intent);
    }

    @Override
    public void onPrintJobQueued(final PrintJob printJob) {
        Log.i(LOG_TAG, "#onPrintJobQueued()");
        mProcessedPrintJobs.put(printJob.getId(), printJob);
        if (printJob.isQueued()) {
            printJob.start();
        }
        
        Intent intent = new Intent(this, MyDialogActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(INTENT_EXTRA_PRINT_JOB_ID, printJob.getId());
        intent.putExtra(INTENT_EXTRA_ACTION_TYPE, ACTION_TYPE_ON_PRINT_JOB_PENDING);
        startActivity(intent);
    }

    void handleRequestCancelPrintJob(PrintJobId printJobId) {
        PrintJob printJob = mProcessedPrintJobs.get(printJobId);
        if (printJob == null) {
            return;
        }
        mProcessedPrintJobs.remove(printJobId);
        if (printJob.isQueued() || printJob.isStarted() || printJob.isBlocked()) {
            mHandler.removeMessages(MyHandler.MSG_HANDLE_DO_PRINT_JOB);
            mHandler.removeMessages(MyHandler.MSG_HANDLE_FAIL_PRINT_JOB);
            printJob.cancel();
        }
    }

    void handleFailPrintJobDelayed(PrintJobId printJobId) {
        Message message = mHandler.obtainMessage(
                MyHandler.MSG_HANDLE_FAIL_PRINT_JOB, printJobId);
        mHandler.sendMessageDelayed(message, STANDARD_DELAY_MILLIS);
    }

    void handleFailPrintJob(PrintJobId printJobId) {
        PrintJob printJob = mProcessedPrintJobs.get(printJobId);
        if (printJob == null) {
            return;
        }
        mProcessedPrintJobs.remove(printJobId);
        if (printJob.isQueued() || printJob.isStarted()) {
            printJob.fail(getString(R.string.fail_reason));
        }
    }

    void handleBlockPrintJobDelayed(PrintJobId printJobId) {
        Message message = mHandler.obtainMessage(
                MyHandler.MSG_HANDLE_BLOCK_PRINT_JOB, printJobId);
        mHandler.sendMessageDelayed(message, STANDARD_DELAY_MILLIS);
    }

    void handleBlockPrintJob(PrintJobId printJobId) {
        final PrintJob printJob = mProcessedPrintJobs.get(printJobId);
        if (printJob == null) {
            return;
        }

        if (printJob.isStarted()) {
            printJob.block("Gimme some rest, dude");
        }
    }

    void handleBlockAndDelayedUnblockPrintJob(PrintJobId printJobId) {
        handleBlockPrintJob(printJobId);

        Message message = mHandler.obtainMessage(
                MyHandler.MSG_HANDLE_UNBLOCK_PRINT_JOB, printJobId);
        mHandler.sendMessageDelayed(message, STANDARD_DELAY_MILLIS);
    }

    private void handleUnblockPrintJob(PrintJobId printJobId) {
        final PrintJob printJob = mProcessedPrintJobs.get(printJobId);
        if (printJob == null) {
            return;
        }

        if (printJob.isBlocked()) {
            printJob.start();
        }
    }

    void handleQueuedPrintJobDelayed(PrintJobId printJobId) {
        final PrintJob printJob = mProcessedPrintJobs.get(printJobId);
        if (printJob == null) {
            return;
        }

        if (printJob.isQueued()) {
            printJob.start();
        }
        Message message = mHandler.obtainMessage(
                MyHandler.MSG_HANDLE_DO_PRINT_JOB, printJobId);
        mHandler.sendMessageDelayed(message, STANDARD_DELAY_MILLIS);
    }

    /**
     * Pretend that the print job has progressed.
     *
     * @param printJobId ID of the print job to progress
     * @param progress the new value to progress to
     */
    void handlePrintJobProgress(@NonNull PrintJobId printJobId, int progress) {
        final PrintJob printJob = mProcessedPrintJobs.get(printJobId);
        if (printJob == null) {
            return;
        }

        if (printJob.isQueued()) {
            printJob.start();
        }

        if (progress == 100) {
            handleQueuedPrintJob(printJobId);
        } else {
            if (Build.VERSION.SDK_INT >= 24) {
                printJob.setProgress((float) progress / 100);
                printJob.setStatus("Printing progress: " + progress + "%");
            }

            Message message = mHandler.obtainMessage(
                    MyHandler.MSG_HANDLE_PRINT_JOB_PROGRESS, progress + 10, 0, printJobId);
            mHandler.sendMessageDelayed(message, 1000);
        }
    }

    void handleQueuedPrintJob(PrintJobId printJobId) {
        final PrintJob printJob = mProcessedPrintJobs.get(printJobId);
        if (printJob == null) {
            return;
        }

        if (printJob.isQueued()) {
            printJob.start();
        }

        try {
            final File file = File.createTempFile(this.getClass().getSimpleName(), ".pdf",
                    getFilesDir());
            mFakePrintTask = new AsyncTask<ParcelFileDescriptor, Void, Void>() {
                @Override
                protected Void doInBackground(ParcelFileDescriptor... params) {
                    InputStream in = new BufferedInputStream(new FileInputStream(
                            params[0].getFileDescriptor()));
                    OutputStream out = null;
                    try {
                        out = new BufferedOutputStream(new FileOutputStream(file));
                        final byte[] buffer = new byte[8192];
                        while (true) {
                            if (isCancelled()) {
                                break;
                            }
                            final int readByteCount = in.read(buffer);
                            if (readByteCount < 0) {
                                break;
                            }
                            out.write(buffer, 0, readByteCount);
                        }
                    } catch (IOException ioe) {
                        throw new RuntimeException(ioe);
                    } finally {
                        try {
                            in.close();
                        } catch (IOException ioe) {
                            /* ignore */
                        }
                        if (out != null) {
                            try {
                                out.close();
                            } catch (IOException ioe) {
                                /* ignore */
                            }
                        }
                        if (isCancelled()) {
                            file.delete();
                        }
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void result) {
                    if (printJob.isStarted()) {
                        printJob.complete();
                    }

                    file.setReadable(true, false);

                    // Quick and dirty to show the file - use a content provider instead.
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.fromFile(file), "application/pdf");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent, null);

                    mFakePrintTask = null;
                }

                @Override
                protected void onCancelled(Void result) {
                    if (printJob.isStarted()) {
                        printJob.cancel();
                    }
                }
            };
            mFakePrintTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                    printJob.getDocument().getData());
        } catch (IOException e) {
            Log.e(LOG_TAG, "Could not create temporary file: %s", e);
        }
    }

    private final class MyHandler extends Handler {
        static final int MSG_HANDLE_DO_PRINT_JOB = 1;
        static final int MSG_HANDLE_FAIL_PRINT_JOB = 2;
        static final int MSG_HANDLE_BLOCK_PRINT_JOB = 3;
        static final int MSG_HANDLE_UNBLOCK_PRINT_JOB = 4;
        static final int MSG_HANDLE_PRINT_JOB_PROGRESS = 5;

        public MyHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case MSG_HANDLE_DO_PRINT_JOB: {
                    PrintJobId printJobId = (PrintJobId) message.obj;
                    handleQueuedPrintJob(printJobId);
                } break;

                case MSG_HANDLE_FAIL_PRINT_JOB: {
                    PrintJobId printJobId = (PrintJobId) message.obj;
                    handleFailPrintJob(printJobId);
                } break;

                case MSG_HANDLE_BLOCK_PRINT_JOB: {
                    PrintJobId printJobId = (PrintJobId) message.obj;
                    handleBlockPrintJob(printJobId);
                } break;

                case MSG_HANDLE_UNBLOCK_PRINT_JOB: {
                    PrintJobId printJobId = (PrintJobId) message.obj;
                    handleUnblockPrintJob(printJobId);
                } break;

                case MSG_HANDLE_PRINT_JOB_PROGRESS: {
                    PrintJobId printJobId = (PrintJobId) message.obj;
                    handlePrintJobProgress(printJobId, message.arg1);
                } break;
            }
        }
    }

    private final class FakePrinterDiscoverySession extends  PrinterDiscoverySession {
        private final Handler mSesionHandler = new SessionHandler(getMainLooper());

        private final List<PrinterInfo> mFakePrinters = new ArrayList<>();

        FakePrinterDiscoverySession() {
            for (int i = 0; i < 6; i++) {
                String name = "Printer " + i;

                PrinterInfo.Builder builder = new PrinterInfo.Builder(generatePrinterId(name), name,
                        (i == 1 || i == 2) ? PrinterInfo.STATUS_UNAVAILABLE
                                : PrinterInfo.STATUS_IDLE);

                if (i != 3) {
                    builder.setDescription("Launch a menu to select behavior.");
                }

                if (i != 4) {
                    if (Build.VERSION.SDK_INT >= 24) {
                        builder.setIconResourceId(R.drawable.printer);
                    }
                }

                if (i % 2 == 0) {
                    if (Build.VERSION.SDK_INT >= 24) {
                        Intent infoIntent = new Intent(MyPrintService.this, InfoActivity.class);
                        infoIntent.putExtra(InfoActivity.PRINTER_NAME, name);

                        PendingIntent infoPendingIntent = PendingIntent.getActivity(
                                getApplicationContext(),
                                i, infoIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                        builder.setInfoIntent(infoPendingIntent);
                    }
                }

                if (i == 5) {
                    if (Build.VERSION.SDK_INT >= 24) {
                        builder.setHasCustomPrinterIcon(true);
                    }
                }

                mFakePrinters.add(builder.build());
            }
        }

        @Override
        public void onDestroy() {
            Log.i(LOG_TAG, "FakePrinterDiscoverySession#onDestroy()");
            mSesionHandler.removeMessages(SessionHandler.MSG_ADD_FIRST_BATCH_FAKE_PRINTERS);
        }

        @Override
        public void onStartPrinterDiscovery(List<PrinterId> priorityList) {
            Log.i(LOG_TAG, "FakePrinterDiscoverySession#onStartPrinterDiscovery()");
            Message message1 = mSesionHandler.obtainMessage(
                    SessionHandler.MSG_ADD_FIRST_BATCH_FAKE_PRINTERS, this);
            mSesionHandler.sendMessageDelayed(message1, 0);
        }

        @Override
        public void onStopPrinterDiscovery() {
            Log.i(LOG_TAG, "FakePrinterDiscoverySession#onStopPrinterDiscovery()");
            cancellAddingFakePrinters();
        }

        @Override
        public void onStartPrinterStateTracking(PrinterId printerId) {
            Log.i(LOG_TAG, "FakePrinterDiscoverySession#onStartPrinterStateTracking()");

            final int printerCount = mFakePrinters.size();
            for (int i = printerCount - 1; i >= 0; i--) {
                PrinterInfo printer = mFakePrinters.remove(i);

                if (printer.getId().equals(printerId)) {
                    PrinterCapabilitiesInfo.Builder b = new PrinterCapabilitiesInfo.Builder(
                            printerId)
                                    .setMinMargins(new Margins(200, 200, 200, 200))
                                    .addMediaSize(MediaSize.ISO_A4, true)
                                    .addMediaSize(MediaSize.NA_GOVT_LETTER, false)
                                    .addMediaSize(MediaSize.JPN_YOU4, false)
                                    .addResolution(new Resolution("R1", getString(
                                            R.string.resolution_200x200), 200, 200), false)
                                    .addResolution(new Resolution("R2", getString(
                                            R.string.resolution_300x300), 300, 300), true)
                                    .setColorModes(PrintAttributes.COLOR_MODE_COLOR
                                            | PrintAttributes.COLOR_MODE_MONOCHROME,
                                            PrintAttributes.COLOR_MODE_MONOCHROME);

                    if (Build.VERSION.SDK_INT >= 23) {
                        b.setDuplexModes(PrintAttributes.DUPLEX_MODE_LONG_EDGE
                                        | PrintAttributes.DUPLEX_MODE_NONE,
                                PrintAttributes.DUPLEX_MODE_LONG_EDGE);
                    }

                    PrinterCapabilitiesInfo capabilities = b.build();

                    printer = new PrinterInfo.Builder(printer)
                            .setCapabilities(capabilities)
                            .build();
                }

                mFakePrinters.add(printer);
            }

            addPrinters(mFakePrinters);
        }

        @Override
        public void onRequestCustomPrinterIcon(final PrinterId printerId,
                final CancellationSignal cancellationSignal,
                final CustomPrinterIconCallback callbacks) {
            Log.i(LOG_TAG, "FakePrinterDiscoverySession#onRequestCustomPrinterIcon() " + printerId);

            SomeArgs args = SomeArgs.obtain();
            args.arg1 = cancellationSignal;
            args.arg2 = callbacks;

            Message msg = mSesionHandler.obtainMessage(
                    SessionHandler.MSG_SUPPLY_CUSTOM_PRINTER_ICON, args);

            // Pretend the bitmap icon takes 5 seconds to load
            mSesionHandler.sendMessageDelayed(msg, 5000);
        }

        @Override
        public void onValidatePrinters(List<PrinterId> printerIds) {
            Log.i(LOG_TAG, "FakePrinterDiscoverySession#onValidatePrinters() " + printerIds);
        }

        @Override
        public void onStopPrinterStateTracking(PrinterId printerId) {
            Log.i(LOG_TAG, "FakePrinterDiscoverySession#onStopPrinterStateTracking()");
        }

        private void addFirstBatchFakePrinters() {
            List<PrinterInfo> printers = mFakePrinters.subList(0, mFakePrinters.size());
            addPrinters(printers);
        }

        private void cancellAddingFakePrinters() {
            mSesionHandler.removeMessages(SessionHandler.MSG_ADD_FIRST_BATCH_FAKE_PRINTERS);
        }

        final class SessionHandler extends Handler {
            static final int MSG_ADD_FIRST_BATCH_FAKE_PRINTERS = 1;
            static final int MSG_SUPPLY_CUSTOM_PRINTER_ICON = 2;

            public SessionHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_ADD_FIRST_BATCH_FAKE_PRINTERS: {
                        addFirstBatchFakePrinters();
                    } break;
                    case MSG_SUPPLY_CUSTOM_PRINTER_ICON: {
                        SomeArgs args = (SomeArgs) message.obj;
                        CancellationSignal cancellationSignal = (CancellationSignal) args.arg1;
                        CustomPrinterIconCallback callbacks = (CustomPrinterIconCallback) args.arg2;
                        args.recycle();

                        if (!cancellationSignal.isCanceled()) {
                            callbacks.onCustomPrinterIconLoaded(Icon.createWithBitmap(
                                    BitmapFactory.decodeResource(getResources(),
                                    R.raw.red_printer)));
                        }
                    } break;
                }
            }
        }
    }
}
