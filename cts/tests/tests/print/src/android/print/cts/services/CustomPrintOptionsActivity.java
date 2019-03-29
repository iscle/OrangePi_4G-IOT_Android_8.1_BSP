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

package android.print.cts.services;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.print.PrintJobInfo;
import android.print.PrinterInfo;
import android.printservice.PrintService;

/**
 * Custom print options activity for both print services
 */
public class CustomPrintOptionsActivity extends Activity {
    /** Lock for {@link #sCallback} */
    private static final Object sLock = new Object();

    /** Currently registered callback for _both_ first and second print service. */
    private static CustomPrintOptionsCallback sCallback = null;

    /**
     * Set a new callback called when the custom options activity is launched.
     *
     * @param callback The new callback or null, if the callback should be unregistered.
     */
    public static void setCallBack(CustomPrintOptionsCallback callback) {
        synchronized (sLock) {
            sCallback = callback;
        }
    }

    /**
     * Callback executed for this activity. Set via {@link #setCallBack}.
     */
    public interface CustomPrintOptionsCallback {
        PrintJobInfo executeCustomPrintOptionsActivity(PrintJobInfo printJob,
                PrinterInfo printer);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent result = new Intent();

        synchronized (sLock) {
            if (sCallback != null) {
                PrintJobInfo printJobInfo = getIntent().getParcelableExtra(
                        PrintService.EXTRA_PRINT_JOB_INFO);
                PrinterInfo printerInfo = getIntent().getParcelableExtra(
                        "android.intent.extra.print.EXTRA_PRINTER_INFO");

                result.putExtra(PrintService.EXTRA_PRINT_JOB_INFO,
                        sCallback.executeCustomPrintOptionsActivity(printJobInfo, printerInfo));
            }
        }

        setResult(Activity.RESULT_OK, result);
        finish();
    }
}
