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

package android.permission.cts;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The security designs of many system features require that a special
 * permission is only ever granted to the core system (typically
 * {@code system_server}), since it's the only process that should be binding
 * into sensitive app code.
 * <p>
 * No apps outside the {@code system_server} should <em>ever</em> attempt to
 * acquire these permissions.
 */
public class ServicePermissionTest extends AndroidTestCase {
    public static String[] sServicePermissions = {
            android.Manifest.permission.ACCOUNT_MANAGER,
            android.Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
            android.Manifest.permission.BIND_AUTOFILL_SERVICE,
            android.Manifest.permission.BIND_CHOOSER_TARGET_SERVICE,
            android.Manifest.permission.BIND_CONDITION_PROVIDER_SERVICE,
            // android.Manifest.permission.BIND_DEVICE_ADMIN,
            android.Manifest.permission.BIND_DREAM_SERVICE,
            android.Manifest.permission.BIND_INPUT_METHOD,
            android.Manifest.permission.BIND_MIDI_DEVICE_SERVICE,
            // android.Manifest.permission.BIND_NFC_SERVICE,
            android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
            android.Manifest.permission.BIND_PRINT_SERVICE,
            // android.Manifest.permission.BIND_QUICK_SETTINGS_TILE,
            android.Manifest.permission.BIND_TEXT_SERVICE,
            android.Manifest.permission.BIND_VOICE_INTERACTION,
            android.Manifest.permission.BIND_VPN_SERVICE,
            android.Manifest.permission.BIND_VR_LISTENER_SERVICE,
    };

    public void testServicePermissions() {
        final PackageManager pm = getContext().getPackageManager();

        final List<String> failures = new ArrayList<>();
        for (String perm : sServicePermissions) {
            final List<PackageInfo> holding = pm.getPackagesHoldingPermissions(
                    new String[] { perm }, PackageManager.MATCH_UNINSTALLED_PACKAGES);
            for (PackageInfo pi : holding) {
                if (!Objects.equals("android", pi.packageName)) {
                    failures.add(perm + " held by " + pi.packageName);
                }
            }
        }
        if (!failures.isEmpty()) {
            fail("Found permissions granted to packages outside of the core system: "
                    + failures.toString());
        }
    }
}
