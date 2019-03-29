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

package android.net.cts;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.util.Log;

import java.lang.Thread;

public class AirplaneModeTest extends AndroidTestCase {
    private static final String TAG = "AirplaneModeTest";
    private static final String FEATURE_BLUETOOTH = "android.hardware.bluetooth";
    private static final String FEATURE_WIFI = "android.hardware.wifi";
    private static final int TIMEOUT_MS = 10 * 1000;
    private boolean mHasFeature;
    private Context mContext;
    private ContentResolver resolver;

    public void setup() {
        mContext= getContext();
        resolver = mContext.getContentResolver();
        mHasFeature = (mContext.getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH)
                       || mContext.getPackageManager().hasSystemFeature(FEATURE_WIFI));
    }

    public void testAirplaneMode() {
        setup();
        if (!mHasFeature) {
            Log.i(TAG, "The device doesn't support network bluetooth or wifi feature");
            return;
        }

        for (int testCount = 0; testCount < 2; testCount++) {
            if (!doOneTest()) {
                fail("Airplane mode failed to change in " + TIMEOUT_MS + "msec");
                return;
            }
        }
    }

    private boolean doOneTest() {
        boolean airplaneModeOn = isAirplaneModeOn();
        setAirplaneModeOn(!airplaneModeOn);

        try {
            Thread.sleep(TIMEOUT_MS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Sleep time interrupted.", e);
        }

        if (airplaneModeOn == isAirplaneModeOn()) {
            return false;
        }
        return true;
    }

    private void setAirplaneModeOn(boolean enabling) {
        // Change the system setting for airplane mode
        Settings.Global.putInt(resolver, Settings.Global.AIRPLANE_MODE_ON, enabling ? 1 : 0);
    }

    private boolean isAirplaneModeOn() {
        // Read the system setting for airplane mode
        return Settings.Global.getInt(mContext.getContentResolver(),
                                      Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }
}
