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

import android.os.CancellationSignal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Custom {@link android.os.CancellationSignal.OnCancelListener} used to assert that
 * {@link android.os.CancellationSignal.OnCancelListener} was called, and just once.
 */
final class OneTimeCancellationSignalListener implements CancellationSignal.OnCancelListener {
    private final CountDownLatch mLatch = new CountDownLatch(1);
    private final long mTimeoutMs;

    OneTimeCancellationSignalListener(long timeoutMs) {
        mTimeoutMs = timeoutMs;
    }

    void assertOnCancelCalled() throws Exception {
        final boolean called = mLatch.await(mTimeoutMs, TimeUnit.MILLISECONDS);
        assertWithMessage("Timeout (%s ms) waiting for onCancel()", mTimeoutMs)
                .that(called).isTrue();
    }

    @Override
    public void onCancel() {
        mLatch.countDown();
    }
}
