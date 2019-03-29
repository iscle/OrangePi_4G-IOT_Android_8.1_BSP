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

import android.os.UserManager;
import android.test.AndroidTestCase;

/**
 * Used after after DO/PO are removed to make sure user restrictions set by them are no longer
 * set.
 */
public class CheckNoOwnerRestrictionsTest extends AndroidTestCase {
    public void testNoOwnerRestrictions() {
        assertFalse("DISALLOW_UNMUTE_MICROPHONE is still set",
                mContext.getSystemService(UserManager.class).hasUserRestriction(
                UserManager.DISALLOW_UNMUTE_MICROPHONE));
    }
}
