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

package android.bluetooth.cts;

import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseSettings;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.HashSet;
import java.util.Set;

/**
 * Test of {@link AdvertiseCallback}.
 */
public class AdvertiseCallbackTest extends AndroidTestCase {

    private final static int ADVERTISE_TYPE_SUCCESS = 0;
    private final static int ADVERTISE_TYPE_FAIL = 1;

    private final MockAdvertiser mMockAdvertiser = new MockAdvertiser();
    private final BleAdvertiseCallback mAdvertiseCallback = new BleAdvertiseCallback();

    @SmallTest
    public void testAdvertiseSuccess() {
        mAdvertiseCallback.mAdvertiseType = ADVERTISE_TYPE_SUCCESS;
        mMockAdvertiser.startAdvertise(mAdvertiseCallback);
    }

    @SmallTest
    public void testAdvertiseFailure() {
        mAdvertiseCallback.mAdvertiseType = ADVERTISE_TYPE_SUCCESS;
        mMockAdvertiser.startAdvertise(mAdvertiseCallback);

        // Second advertise with the same callback should fail.
        mAdvertiseCallback.mAdvertiseType = ADVERTISE_TYPE_FAIL;
        mMockAdvertiser.startAdvertise(mAdvertiseCallback);
    }

    // A mock advertiser which emulate BluetoothLeAdvertiser behavior.
    private static class MockAdvertiser {
        private Set<AdvertiseCallback> mCallbacks = new HashSet<>();

        void startAdvertise(AdvertiseCallback callback) {
            synchronized (mCallbacks) {
                if (mCallbacks.contains(callback)) {
                    callback.onStartFailure(AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED);
                } else {
                    callback.onStartSuccess(null);
                    mCallbacks.add(callback);
                }
            }
        }
    }

    private static class BleAdvertiseCallback extends AdvertiseCallback {
        int mAdvertiseType = ADVERTISE_TYPE_SUCCESS;

        @Override
        public void onStartSuccess(AdvertiseSettings settings) {
            if (mAdvertiseType == ADVERTISE_TYPE_FAIL) {
                fail("advertise should fail");
            }
        }

        @Override
        public void onStartFailure(int error) {
            if (mAdvertiseType == ADVERTISE_TYPE_SUCCESS) {
                assertEquals(AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED, error);
            }
        }
    }
}
