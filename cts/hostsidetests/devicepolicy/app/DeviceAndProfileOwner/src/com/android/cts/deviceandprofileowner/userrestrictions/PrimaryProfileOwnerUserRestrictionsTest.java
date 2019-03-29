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
package com.android.cts.deviceandprofileowner.userrestrictions;

public class PrimaryProfileOwnerUserRestrictionsTest extends BaseUserRestrictionsTest {
    @Override
    protected String[] getAllowedRestrictions() {
        // PO on user-0 can set DO user restrictions too. (for now?)
        return DeviceOwnerUserRestrictionsTest.ALLOWED;
    }

    @Override
    protected String[] getDisallowedRestrictions() {
        return DeviceOwnerUserRestrictionsTest.DISALLOWED;
    }

    @Override
    protected String[] getDefaultEnabledRestrictions() {
        return new String[0];
    }
}
