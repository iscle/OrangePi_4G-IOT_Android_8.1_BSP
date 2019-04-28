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

package com.android.documentsui.prefs;

import android.content.SharedPreferences;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.testing.TestConsumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class PreferencesMonitorTest {

    private SharedPreferences mPrefs;
    private PreferencesMonitor mMonitor;
    private TestConsumer<String> mConsumer;

    @Before
    public void setUp() {
        mConsumer = new TestConsumer<>();
        mPrefs = InstrumentationRegistry.getContext().getSharedPreferences("MonitorTest0", 0);
        mMonitor = new PreferencesMonitor("Poodles", mPrefs, mConsumer);
        mMonitor.start();
    }

    @After
    public void tearDown() {
        mMonitor.stop();
    }

    @Test
    public void testReportsChangesToListener() throws Exception {
      mPrefs.edit().putBoolean(ScopedPreferences.INCLUDE_DEVICE_ROOT, true).apply();
      // internally the monitor waits for notification of changes.
      mConsumer.waitForCall(100, TimeUnit.MILLISECONDS);
      mConsumer.assertLastArgument(ScopedPreferences.INCLUDE_DEVICE_ROOT);
    }
}
