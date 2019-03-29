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
package com.android.cts.deviceandprofileowner.userrestrictions;

import android.os.UserManager;

/**
 * Test for managed profile owner restriction behavior. It is mostly the same as just secondary
 * profile owner but some restrictions are set by default.
 */
public class ManagedProfileOwnerUserRestrictionsTest
        extends SecondaryProfileOwnerUserRestrictionsTest {

    private static final String[] DEFAULT_ENABLED = new String[] {
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_BLUETOOTH_SHARING
    };

    @Override
    protected String[] getDefaultEnabledRestrictions() {
        return DEFAULT_ENABLED;
    }
}