
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
package com.android.cts.managedprofile;

import static com.android.cts.managedprofile.BluetoothSharingRestrictionTest.assertBluetoothSharingAvailable;

import android.bluetooth.BluetoothAdapter;
import android.test.InstrumentationTestCase;

/**
 * Auxiliary test to check that Bluetooth sharing in primary profile is not affected.
 */
public class BluetoothSharingRestrictionPrimaryProfileTest extends InstrumentationTestCase {
    /** Verifies that bluetooth sharing is available in personal profile. */
    public void testBluetoothSharingAvailable() throws Exception {
        if (BluetoothAdapter.getDefaultAdapter() == null) {
            // No Bluetooth - nothing to test.
            return;
        }
        assertBluetoothSharingAvailable(getInstrumentation().getContext(), true);
    }
}
