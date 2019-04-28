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

package com.android.managedprovisioning.common;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
public class MdmPackageInfoTest extends AndroidTestCase {
    private final static Drawable TEST_DRAWABLE = new ColorDrawable(0);
    private final static String TEST_PACKAGE_NAME = "com.test.mdm";
    private final static String TEST_LABEL = "Test app";

    @Mock private Context mockContext;
    @Mock private PackageManager mockPackageManager;

    private final ApplicationInfo mApplicationInfo = new ApplicationInfo();

    @Override
    public void setUp() throws Exception {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        when(mockContext.getPackageManager()).thenReturn(mockPackageManager);
        when(mockPackageManager.getApplicationInfo(TEST_PACKAGE_NAME, 0))
                .thenReturn(mApplicationInfo);
        when(mockPackageManager.getApplicationIcon(TEST_PACKAGE_NAME)).thenReturn(TEST_DRAWABLE);
        when(mockPackageManager.getApplicationLabel(mApplicationInfo)).thenReturn(TEST_LABEL);
    }

    public void testConstructor() {
        // GIVEN an app icon and an app label
        // WHEN MdmPackageInfo is constructed
        MdmPackageInfo mdmInfo = new MdmPackageInfo(TEST_DRAWABLE, TEST_LABEL);
        // THEN the app icon and app label are stored in the MdmPackageInfo object
        assertSame(TEST_DRAWABLE, mdmInfo.packageIcon);
        assertEquals(TEST_LABEL, mdmInfo.appLabel);
    }

    public void testCreateFromPackageName() {
        // GIVEN a package name
        // WHEN MdmPackageInfo is created from package name
        MdmPackageInfo mdmInfo = MdmPackageInfo.createFromPackageName(mockContext,
                TEST_PACKAGE_NAME);
        // THEN the app icon and app label are loaded correctly from the package manager
        assertSame(TEST_DRAWABLE, mdmInfo.packageIcon);
        assertEquals(TEST_LABEL, mdmInfo.appLabel);
    }

    public void testCreateFromPackageName_NameNotFoundException() throws Exception {
        // GIVEN that the package does not exist on the device
        // WHEN MdmPackageInfo is created from package name
        when(mockPackageManager.getApplicationInfo(TEST_PACKAGE_NAME, 0))
                .thenThrow(new NameNotFoundException());
        // THEN null is returned
        assertNull(MdmPackageInfo.createFromPackageName(mockContext, TEST_PACKAGE_NAME));
    }
}
