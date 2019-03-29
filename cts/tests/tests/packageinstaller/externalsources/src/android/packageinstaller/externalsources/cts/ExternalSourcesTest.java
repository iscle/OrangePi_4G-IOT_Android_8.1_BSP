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
package android.packageinstaller.externalsources.cts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ExternalSourcesTest {

    private Context mContext;
    private PackageManager mPm;
    private String mPackageName;
    private UiDevice mUiDevice;
    private boolean mHasFeature;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mPm = mContext.getPackageManager();
        mPackageName = mContext.getPackageName();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mHasFeature = !mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    private void setAppOpsMode(String mode) throws IOException {
        final StringBuilder commandBuilder = new StringBuilder("appops set");
        commandBuilder.append(" " + mPackageName);
        commandBuilder.append(" REQUEST_INSTALL_PACKAGES");
        commandBuilder.append(" " + mode);
        mUiDevice.executeShellCommand(commandBuilder.toString());
    }

    @Test
    public void blockedSourceTest() throws Exception {
        setAppOpsMode("deny");
        final boolean isTrusted = mPm.canRequestPackageInstalls();
        Assert.assertFalse("Package " + mPackageName
                + " allowed to install packages after setting app op to errored", isTrusted);
    }

    @Test
    public void allowedSourceTest() throws Exception {
        setAppOpsMode("allow");
        final boolean isTrusted = mPm.canRequestPackageInstalls();
        Assert.assertTrue("Package " + mPackageName
                + " blocked from installing packages after setting app op to allowed", isTrusted);
    }

    @Test
    public void defaultSourceTest() throws Exception {
        setAppOpsMode("default");
        final boolean isTrusted = mPm.canRequestPackageInstalls();
        Assert.assertFalse("Package " + mPackageName
                + " with default app ops state allowed to install packages", isTrusted);
    }

    @Test
    public void testManageUnknownSourcesExists() {
        if (!mHasFeature) {
            return;
        }
        Intent manageUnknownSources = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
        ResolveInfo info = mPm.resolveActivity(manageUnknownSources, 0);
        Assert.assertNotNull("No activity found for " + manageUnknownSources.getAction(), info);
    }

    @After
    public void tearDown() throws Exception {
        setAppOpsMode("default");
    }
}
