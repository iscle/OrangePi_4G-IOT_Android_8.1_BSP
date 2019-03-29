/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.keysets;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.test.AndroidTestCase;

import java.lang.Override;

/**
 * KeySets device-side tests involving permissions
 */
public class KeySetPermissionsTest extends AndroidTestCase {

    private static final String KEYSET_APP_PKG = "com.android.cts.keysets";
    private static final String KEYSET_PERM_DEF_PKG = "com.android.cts.keysets_permdef";
    private static final String KEYSET_PERM_NAME = "com.android.cts.keysets_permdef.keysets_perm";

    public void testHasPerm() throws Exception {
        PackageManager pm = getContext().getPackageManager();
        assertTrue(KEYSET_PERM_NAME + " not granted to " + KEYSET_APP_PKG,
                pm.checkPermission(KEYSET_PERM_NAME, KEYSET_APP_PKG) == PackageManager.PERMISSION_GRANTED);
    }
}
