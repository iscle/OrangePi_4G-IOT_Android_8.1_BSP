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

package android.autofillservice.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.view.autofill.AutofillManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
  * Base class for all activities in this test suite
  */
abstract class AbstractAutoFillActivity extends Activity {

    private MyAutofillCallback mCallback;

    /**
     * Run an action in the UI thread, and blocks caller until the action is finished.
     */
    public final void syncRunOnUiThread(Runnable action) {
        syncRunOnUiThread(action, Helper.UI_TIMEOUT_MS);
    }

    /**
     * Run an action in the UI thread, and blocks caller until the action is finished or it times
     * out.
     */
    public final void syncRunOnUiThread(Runnable action, int timeoutMs) {
        final CountDownLatch latch = new CountDownLatch(1);
        runOnUiThread(() -> {
            action.run();
            latch.countDown();
        });
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new RetryableException("action on UI thread timed out after %d ms",
                        timeoutMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }

    protected AutofillManager getAutofillManager() {
        return getSystemService(AutofillManager.class);
    }

    /**
     * Registers and returns a custom callback for autofill events.
     */
    protected MyAutofillCallback registerCallback() {
        assertWithMessage("already registered").that(mCallback).isNull();
        mCallback = new MyAutofillCallback();
        getAutofillManager().registerCallback(mCallback);
        return mCallback;
    }

    /**
     * Unregister the callback from the {@link AutofillManager}.
     */
    protected void unregisterCallback() {
        assertWithMessage("not registered").that(mCallback).isNotNull();
        getAutofillManager().unregisterCallback(mCallback);
        mCallback = null;
    }
}
