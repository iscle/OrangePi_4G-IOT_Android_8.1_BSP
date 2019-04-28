/*
 * Copyright 2016, The Android Open Source Project
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

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_CONTENT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_HEADER;
import static com.android.managedprovisioning.common.StoreUtils.DIR_PROVISIONING_PARAMS_FILE_CACHE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.model.DisclaimersParam;
import com.android.managedprovisioning.testcommon.TestUtils;
import com.android.managedprovisioning.tests.R;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link DisclaimersParser}.
 */
@SmallTest
public class DisclaimersParserTest {
    private static final String TEST_FILE_DIRNAME = "DisclaimersParserTest";
    private static final String TEST_PACKAGE = "com.android.managedprovisioning.tests";
    private static final long TEST_PROVISIONING_ID = 999L;

    private static File TEST_FILE_DIR;

    private static Uri DISCLAIMER_URI_1;
    private static final String DISCLAIMER_HEADER_1 = "DISCLAIMER_HEADER_1";
    private static String DISCLAIMER_CONTENT_1;
    private static String DISCLAIMER_FILE_DEST_1;
    private static Uri DISCLAIMER_URI_2;
    private static final String DISCLAIMER_HEADER_2 = "DISCLAIMER_HEADER_2";
    private static String DISCLAIMER_CONTENT_2;
    private static String DISCLAIMER_FILE_DEST_2;
    private static Uri DISCLAIMER_URI_3;
    private static final String DISCLAIMER_HEADER_3 = "DISCLAIMER_HEADER_3";
    private static String DISCLAIMER_CONTENT_3;
    private static String DISCLAIMER_FILE_DEST_3;

    @Mock
    Context mContext;

    DisclaimersParser mDisclaimersParser;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Stop the activity from rotating in order to keep hold of the context
        Context testContext = InstrumentationRegistry.getContext();
        Context targetContext = InstrumentationRegistry.getTargetContext();
        ContentResolver cr = targetContext.getContentResolver();
        TEST_FILE_DIR = new File(targetContext.getFilesDir(), TEST_FILE_DIRNAME);

        DISCLAIMER_URI_1 = TestUtils.resourceToUri(testContext, R.raw.test_disclaimer1);
        DISCLAIMER_CONTENT_1 = TestUtils.stringFromUri(cr, DISCLAIMER_URI_1);
        DISCLAIMER_FILE_DEST_1 = getDisclaimerPath(1);
        DISCLAIMER_URI_2 = TestUtils.resourceToUri(testContext, R.raw.test_disclaimer2);
        DISCLAIMER_CONTENT_2 = TestUtils.stringFromUri(cr, DISCLAIMER_URI_2);
        DISCLAIMER_FILE_DEST_2 = getDisclaimerPath(2);
        DISCLAIMER_URI_3 = TestUtils.resourceToUri(testContext, R.raw.test_disclaimer3);
        DISCLAIMER_CONTENT_3 = TestUtils.stringFromUri(cr, DISCLAIMER_URI_3);
        DISCLAIMER_FILE_DEST_3 = getDisclaimerPath(3);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        when(mContext.getFilesDir()).thenReturn(TEST_FILE_DIR);
        when(mContext.getContentResolver()).thenReturn(cr);

        mDisclaimersParser = new DisclaimersParser(mContext, TEST_PROVISIONING_ID);
    }

    @After
    public void tearDown() {
        TestUtils.deleteRecursive(TEST_FILE_DIR);
    }

    @Test
    public void testEmpty() {
        assertNull(mDisclaimersParser.parse(null));
        assertNull(mDisclaimersParser.parse(new Bundle[0]));
    }

    @Test
    public void testHeaderOnly() {
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_PROVISIONING_DISCLAIMER_HEADER, DISCLAIMER_HEADER_1);
        assertNull(mDisclaimersParser.parse(new Bundle[] { bundle }));
    }

    @Test
    public void testOneParsing() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_PROVISIONING_DISCLAIMER_HEADER, DISCLAIMER_HEADER_1);
        bundle.putParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT, DISCLAIMER_URI_1);

        DisclaimersParam disclaimers = mDisclaimersParser.parse(new Bundle[] { bundle });

        assertNotNull(disclaimers);
        assertEquals(disclaimers.mDisclaimers.length, 1);
        assertEquals(disclaimers.mDisclaimers[0].mHeader, DISCLAIMER_HEADER_1);
        assertEquals(disclaimers.mDisclaimers[0].mContentFilePath, DISCLAIMER_FILE_DEST_1);
        assertEquals(getDisclaimerContentString(disclaimers.mDisclaimers[0]),
                DISCLAIMER_CONTENT_1);
    }

    @Test
    public void testThreeParsing() throws Exception {
        Bundle bundle1 = new Bundle();
        bundle1.putString(EXTRA_PROVISIONING_DISCLAIMER_HEADER, DISCLAIMER_HEADER_1);
        bundle1.putParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT, DISCLAIMER_URI_1);
        Bundle bundle2 = new Bundle();
        bundle2.putString(EXTRA_PROVISIONING_DISCLAIMER_HEADER, DISCLAIMER_HEADER_2);
        bundle2.putParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT, DISCLAIMER_URI_2);
        Bundle bundle3 = new Bundle();
        bundle3.putString(EXTRA_PROVISIONING_DISCLAIMER_HEADER, DISCLAIMER_HEADER_3);
        bundle3.putParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT, DISCLAIMER_URI_3);

        DisclaimersParam disclaimers = mDisclaimersParser.parse(
                new Bundle[] { bundle1, bundle2, bundle3 });

        // The order of disclaimers must follow the original orders
        assertNotNull(disclaimers);
        assertEquals(disclaimers.mDisclaimers.length, 3);
        assertEquals(disclaimers.mDisclaimers[0].mHeader, DISCLAIMER_HEADER_1);
        assertEquals(disclaimers.mDisclaimers[0].mContentFilePath, DISCLAIMER_FILE_DEST_1);
        assertEquals(getDisclaimerContentString(disclaimers.mDisclaimers[0]),
                DISCLAIMER_CONTENT_1);
        assertEquals(disclaimers.mDisclaimers[1].mHeader, DISCLAIMER_HEADER_2);
        assertEquals(disclaimers.mDisclaimers[1].mContentFilePath, DISCLAIMER_FILE_DEST_2);
        assertEquals(getDisclaimerContentString(disclaimers.mDisclaimers[1]),
                DISCLAIMER_CONTENT_2);
        assertEquals(disclaimers.mDisclaimers[2].mHeader, DISCLAIMER_HEADER_3);
        assertEquals(disclaimers.mDisclaimers[2].mContentFilePath, DISCLAIMER_FILE_DEST_3);
        assertEquals(getDisclaimerContentString(disclaimers.mDisclaimers[2]),
                DISCLAIMER_CONTENT_3);
    }

    private String getDisclaimerContentString(DisclaimersParam.Disclaimer disclaimer)
            throws IOException {
        return StoreUtils.readString(new File(disclaimer.mContentFilePath));
    }

    private static String getDisclaimerPath(int index) {
        return new File(new File(TEST_FILE_DIR, DIR_PROVISIONING_PARAMS_FILE_CACHE),
                "disclaimer_content_" + TEST_PROVISIONING_ID + "_" + ( index - 1 ) + ".txt")
                .getAbsolutePath();
    }
}
