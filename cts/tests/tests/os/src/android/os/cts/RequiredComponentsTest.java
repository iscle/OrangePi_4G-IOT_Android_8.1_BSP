/*
 * Copyright (C) 2016 Google Inc.
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

package android.os.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;

/**
 * Tests whether all platform components that are implemented
 * as APKs for various reasons are present.
 */
@RunWith(AndroidJUnit4.class)
public class RequiredComponentsTest {
    private static final String PACKAGE_MIME_TYPE = "application/vnd.android.package-archive";

    @Test
    public void testPackageInstallerPresent() throws Exception {
        Intent installerIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        installerIntent.addCategory(Intent.CATEGORY_DEFAULT);
        installerIntent.setDataAndType(Uri.fromFile(new File("foo.apk")), PACKAGE_MIME_TYPE);
        List<ResolveInfo> installers = InstrumentationRegistry.getContext()
                .getPackageManager().queryIntentActivities(installerIntent, MATCH_SYSTEM_ONLY
                        | MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);
        if (installers.size() == 1) {
            ResolveInfo resolveInfo = installers.get(0);
            if (!resolveInfo.activityInfo.applicationInfo.isPrivilegedApp()) {
                fail("The installer must be a privileged app");
            }
        } else {
            fail("There must be exactly one installer; found " + installers);
        }
    }

    @Test
    public void testExtServicesPresent() throws Exception {
        enforceSharedLibPresentAndProperlyHosted(
                PackageManager.SYSTEM_SHARED_LIBRARY_SERVICES,
                ApplicationInfo.FLAG_SYSTEM,
                ApplicationInfo.PRIVATE_FLAG_PRIVILEGED);
    }

    @Test
    public void testSharedServicesPresent() throws Exception {
        enforceSharedLibPresentAndProperlyHosted(
                PackageManager.SYSTEM_SHARED_LIBRARY_SHARED,
                ApplicationInfo.FLAG_SYSTEM, 0);
    }

    private void enforceSharedLibPresentAndProperlyHosted(String libName,
            int requiredHostAppFlags, int requiredHostAppPrivateFlags) throws Exception {
        PackageManager packageManager = InstrumentationRegistry.getContext()
                .getPackageManager();

        // Is the lib present?
        String[] libs = packageManager.getSystemSharedLibraryNames();
        boolean libPresent = false;
        for (String lib : libs) {
            if (libName.equals(lib)) {
                libPresent = true;
                break;
            }
        }
        if (!libPresent) {
            fail("Missing required shared library:" + libName);
        }

        // Is it properly hosted?
        String packageName = packageManager.getServicesSystemSharedLibraryPackageName();
        PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);

        assertTrue(libName + " must be hosted by a system app with flags:"
                + requiredHostAppFlags, (packageInfo.applicationInfo.flags
                & requiredHostAppFlags) == requiredHostAppFlags);

        assertTrue(libName + " must be hosted by a system app with private flags:"
                + requiredHostAppPrivateFlags, (packageInfo.applicationInfo.privateFlags
                & requiredHostAppPrivateFlags) == requiredHostAppPrivateFlags);
    }
}
