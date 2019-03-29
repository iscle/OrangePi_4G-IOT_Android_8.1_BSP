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

package com.android.cts.escalatepermission;

import android.content.Context;
import android.content.pm.PermissionInfo;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertSame;

import com.android.cts.escalate.permission.Manifest;

@RunWith(AndroidJUnit4.class)
public class PermissionEscalationTest {
    @Test
    public void testCannotEscalateNonRuntimePermissionsToRuntime() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();

        // Ensure normal permission cannot be made dangerous
        PermissionInfo stealAudio1Permission1 = context.getPackageManager()
                .getPermissionInfo(Manifest.permission.STEAL_AUDIO1, 0);
        assertSame("Shouldn't be able to change normal permission to dangerous",
                PermissionInfo.PROTECTION_NORMAL, (stealAudio1Permission1.protectionLevel
                        & PermissionInfo.PROTECTION_MASK_BASE));

        // Ensure signature permission cannot be made dangerous
        PermissionInfo stealAudio1Permission2 = context.getPackageManager()
                .getPermissionInfo(Manifest.permission.STEAL_AUDIO2, 0);
        assertSame("Shouldn't be able to change signature permission to dangerous",
                PermissionInfo.PROTECTION_SIGNATURE, (stealAudio1Permission2.protectionLevel
                        & PermissionInfo.PROTECTION_MASK_BASE));
     }
 }
