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

package android.widget.toast.cts;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import org.junit.Before;

/**
 * Base class for toast tests.
 */
public abstract class BaseToastTest {
    protected static final long TOAST_TIMEOUT_MILLIS = 5000; // 5 sec
    protected static final long EVENT_TIMEOUT_MILLIS = 5000; // 5 sec

    protected Context mContext;
    protected Instrumentation mInstrumentation;
    protected UiAutomation mUiAutomation;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mUiAutomation = mInstrumentation.getUiAutomation();
        waitForToastTimeout();
    }

    protected void waitForToastTimeout() {
        SystemClock.sleep(TOAST_TIMEOUT_MILLIS);
    }

    protected void showToastsViaToastApis(int count) throws Exception {
        Exception[] exceptions = new Exception[1];
        mInstrumentation.runOnMainSync(
                () -> {
                    try {
                        for (int i = 0; i < count; i++) {
                            Toast.makeText(mContext, getClass().getName(),
                                    Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        exceptions[0] = e;
                    }
                });
        if (exceptions[0] != null) {
            throw exceptions[0];
        }
    }

    protected void showToastsViaAddingWindow(int count, boolean focusable) throws Exception {
        Exception[] exceptions = new Exception[1];
        mInstrumentation.runOnMainSync(() -> {
            try {
                for (int i = 0; i < count; i++) {
                    WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                    params.width = WindowManager.LayoutParams.WRAP_CONTENT;
                    params.format = PixelFormat.TRANSLUCENT;
                    params.type = WindowManager.LayoutParams.TYPE_TOAST;
                    params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
                    if (!focusable) {
                        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                    }

                    TextView textView = new TextView(mContext);
                    textView.setText(BaseToastTest.class.getName());

                    WindowManager windowManager = mContext
                            .getSystemService(WindowManager.class);
                    windowManager.addView(textView, params);
                }
            } catch (Exception e) {
                exceptions[0] = e;
            }
        });
        if (exceptions[0] != null) {
            throw exceptions[0];
        }
    }
}
