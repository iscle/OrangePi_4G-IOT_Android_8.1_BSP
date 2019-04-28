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
package android.platform.longevity.listeners;

import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.VisibleForTesting;

import java.util.concurrent.TimeUnit;

import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;

/**
 * An {@link ActionListener} for terminating early on test end due to long duration.
 */
public final class TimeoutTerminator extends RunTerminator {
    @VisibleForTesting
    static final String OPTION = "suite-timeout_msec";
    private static final long DEFAULT = TimeUnit.MINUTES.toMillis(30L);
    private static final long UNSET_TIMESTAMP = -1;

    private long mStartTimestamp = UNSET_TIMESTAMP;
    private long mSuiteTimeout;

    public TimeoutTerminator(RunNotifier notifier, Bundle args) {
        super(notifier);
        mSuiteTimeout = Long.parseLong(args.getString(OPTION, String.valueOf(DEFAULT)));
    }

    /**
     * {@inheritDoc}
     *
     * Note: this initializes the countdown timer if unset.
     */
    @Override
    public void testStarted(Description description) {
        if (mStartTimestamp == UNSET_TIMESTAMP) {
            mStartTimestamp = SystemClock.uptimeMillis();
        }
    }

    @Override
    public void testFinished(Description description) {
        if (mStartTimestamp != UNSET_TIMESTAMP &&
                (SystemClock.uptimeMillis() - mStartTimestamp) > mSuiteTimeout) {
            kill("the suite timed out");
        }
    }
}
