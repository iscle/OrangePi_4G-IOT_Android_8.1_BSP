/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.CallLog;
import android.provider.Contacts;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import java.util.List;
import java.util.Objects;

/**
 * Tests Permissions related to reading from and writing to providers
 */
@MediumTest
public class ProviderPermissionTest extends AndroidTestCase {
    /**
     * Verify that read and write to contact requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#READ_CONTACTS}
     */
    public void testReadContacts() {
        assertReadingContentUriRequiresPermission(Contacts.People.CONTENT_URI,
                android.Manifest.permission.READ_CONTACTS);
    }

    /**
     * Verify that write to contact requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#WRITE_CONTACTS}
     */
    public void testWriteContacts() {
        assertWritingContentUriRequiresPermission(Contacts.People.CONTENT_URI,
                android.Manifest.permission.WRITE_CONTACTS);
    }

    /**
     * Verify that reading call logs requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#READ_CALL_LOG}
     */
    public void testReadCallLog() {
        assertReadingContentUriRequiresPermission(CallLog.CONTENT_URI,
                android.Manifest.permission.READ_CALL_LOG);
    }

    /**
     * Verify that writing call logs requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#WRITE_CALL_LOG}
     */
    public void testWriteCallLog() {
        assertWritingContentUriRequiresPermission(CallLog.CONTENT_URI,
                android.Manifest.permission.WRITE_CALL_LOG);
    }

    /**
     * Verify that write to settings requires permissions.
     * <p>Tests Permission:
     *   {@link android.Manifest.permission#WRITE_SETTINGS}
     */
    public void testWriteSettings() {
        final String permission = android.Manifest.permission.WRITE_SETTINGS;
        ContentValues value = new ContentValues();
        value.put(Settings.System.NAME, "name");
        value.put(Settings.System.VALUE, "value_insert");

        try {
            getContext().getContentResolver().insert(Settings.System.CONTENT_URI, value);
            fail("expected SecurityException requiring " + permission);
        } catch (SecurityException expected) {
            assertNotNull("security exception's error message.", expected.getMessage());
            assertTrue("error message should contain \"" + permission + "\". Got: \""
                    + expected.getMessage() + "\".",
                    expected.getMessage().contains(permission));
        }
    }

    /**
     * Verify that the {@link android.Manifest.permission#MANAGE_DOCUMENTS}
     * permission is only held by exactly one package: whoever handles the
     * {@link android.content.Intent#ACTION_OPEN_DOCUMENT} intent.
     * <p>
     * No other apps should <em>ever</em> attempt to acquire this permission,
     * since it would give those apps extremely broad access to all storage
     * providers on the device without user involvement in the arbitration
     * process. Apps should instead always rely on Uri permission grants for
     * access, using
     * {@link android.content.Intent#FLAG_GRANT_READ_URI_PERMISSION} and related
     * APIs.
     */
    public void testManageDocuments() {
        final PackageManager pm = getContext().getPackageManager();

        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        final ResolveInfo ri = pm.resolveActivity(intent, 0);
        final String validPkg = ri.activityInfo.packageName;

        final List<PackageInfo> holding = pm.getPackagesHoldingPermissions(new String[] {
                android.Manifest.permission.MANAGE_DOCUMENTS
        }, PackageManager.MATCH_UNINSTALLED_PACKAGES);
        for (PackageInfo pi : holding) {
            if (!Objects.equals(pi.packageName, validPkg)) {
                fail("Exactly one package (must be " + validPkg
                        + ") can request the MANAGE_DOCUMENTS permission; found package "
                        + pi.packageName + " which must be revoked for security reasons");
            }
        }
    }
}
