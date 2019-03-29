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

package com.android.cts.verifier.location;

import android.location.LocationManager;
import android.provider.Settings.Secure;
import com.android.cts.verifier.R;

public class LocationModeOffTestActivity extends LocationModeTestActivity {

    @Override
    protected void createTestItems() {
        createUserItem(R.string.location_mode_turn_off);
        createAutoItem(R.string.location_mode_secure_gps_off);
        createAutoItem(R.string.location_mode_secure_nlp_off);
        createAutoItem(R.string.location_mode_manager_gps_off);
        createAutoItem(R.string.location_mode_manager_nlp_off);
    }

    @Override
    protected void setInfoResources() {
        setInfoResources(R.string.location_mode_off_test,
                R.string.location_mode_off_info, -1);
    }

    @Override
    protected void testAdvance(int state) {
        switch (state) {
            case 0:
                testIsExpectedMode(0, Secure.LOCATION_MODE_OFF);
                break;
            case 1:
                testSecureProviderIsDisabled(1, LocationManager.GPS_PROVIDER);
                break;
            case 2:
                testSecureProviderIsDisabled(2, LocationManager.NETWORK_PROVIDER);
                break;
            case 3:
                testManagerProviderIsDisabled(3, LocationManager.GPS_PROVIDER);
                break;
            case 4:
                testManagerProviderIsDisabled(4, LocationManager.NETWORK_PROVIDER);
                break;
        }
    }
}
