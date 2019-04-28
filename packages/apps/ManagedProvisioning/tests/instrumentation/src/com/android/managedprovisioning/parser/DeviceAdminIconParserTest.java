/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.parser;

import static com.android.managedprovisioning.common.StoreUtils.DIR_PROVISIONING_PARAMS_FILE_CACHE;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.testcommon.TestUtils;
import com.android.managedprovisioning.tests.R;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test for {@link DeviceAdminIconParser}
 */
@SmallTest
public class DeviceAdminIconParserTest {
    private static final String TEST_FILE_DIRNAME = "DeviceAdminIconParserTest";
    private static final long TEST_PROVISIONING_ID = 999L;
    private static File TEST_FILE_DIR;
    private static File OUTPUT_FILE;
    private static Uri INPUT_URI;
    private static String INPUT_CONTENT;

    @Mock
    Context mContext;

    DeviceAdminIconParser mDeviceAdminIconParser;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Stop the activity from rotating in order to keep hold of the context
        Context testContext = InstrumentationRegistry.getContext();
        Context targetContext = InstrumentationRegistry.getTargetContext();
        ContentResolver cr = targetContext.getContentResolver();
        TEST_FILE_DIR = new File(targetContext.getFilesDir(), TEST_FILE_DIRNAME);
        OUTPUT_FILE = new File(new File(TEST_FILE_DIR, DIR_PROVISIONING_PARAMS_FILE_CACHE),
                "device_admin_icon_" + TEST_PROVISIONING_ID);

        INPUT_URI = TestUtils.resourceToUri(testContext, R.raw.android);
        INPUT_CONTENT = TestUtils.stringFromUri(cr, INPUT_URI);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        Context targetContext = InstrumentationRegistry.getTargetContext();

        TEST_FILE_DIR.mkdir();
        when(mContext.getFilesDir()).thenReturn(TEST_FILE_DIR);
        when(mContext.getContentResolver()).thenReturn(targetContext.getContentResolver());

        mDeviceAdminIconParser = new DeviceAdminIconParser(mContext, TEST_PROVISIONING_ID);
    }

    @After
    public void tearDown() {
        deleteRecursive(TEST_FILE_DIR);
    }

    @Test
    public void testNullUri() {
        assertNull(mDeviceAdminIconParser.parse(null));
    }

    @Test
    public void testUri() throws Exception {
        assertEquals(OUTPUT_FILE.getAbsolutePath(), mDeviceAdminIconParser.parse(INPUT_URI));
        assertEquals(INPUT_CONTENT, StoreUtils.readString(OUTPUT_FILE));
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }
}
