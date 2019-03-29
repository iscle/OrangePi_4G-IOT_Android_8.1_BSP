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

package com.android.cts.documentclient;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.android.cts.documentclient.MyActivity.Result;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetFileDescriptor.AutoCloseInputStream;
import android.database.Cursor;
import android.net.Uri;
import android.support.test.uiautomator.Configurator;
import android.support.test.uiautomator.UiDevice;
import android.test.InstrumentationTestCase;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.MotionEvent;

/**
 * Base class for DocumentsUI test cases.
 */
abstract class DocumentsClientTestCase extends InstrumentationTestCase {
    protected static final long TIMEOUT = 30 * DateUtils.SECOND_IN_MILLIS;
    protected static final int REQUEST_CODE = 42;

    static final String PROVIDER_PACKAGE = "com.android.cts.documentprovider";

    private static final String TAG = "DocumentsClientTestCase";

    protected UiDevice mDevice;
    protected MyActivity mActivity;

    private String[] mDisabledImes;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Configurator.getInstance().setToolType(MotionEvent.TOOL_TYPE_FINGER);

        // Disable IME's to avoid virtual keyboards from showing up. Occasionally IME draws some UI
        // controls out of its boundary for some first time setup that obscures the text edit and/or
        // save/select button. This will constantly fail some of our tests.
        disableImes();

        mDevice = UiDevice.getInstance(getInstrumentation());
        mActivity = launchActivity(getInstrumentation().getTargetContext().getPackageName(),
                MyActivity.class, null);
        mDevice.waitForIdle();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mActivity.finish();

        enableImes();
    }

    protected String getColumn(Uri uri, String column) {
        final ContentResolver resolver = getInstrumentation().getContext().getContentResolver();
        final Cursor cursor = resolver.query(uri, new String[] { column }, null, null, null);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        } finally {
            cursor.close();
        }
    }

    protected byte[] readFully(Uri uri) throws IOException {
        final InputStream in = getInstrumentation().getContext().getContentResolver()
                .openInputStream(uri);
        return readFully(in);
    }

    protected byte[] readTypedFully(Uri uri, String mimeType) throws IOException {
        final AssetFileDescriptor descriptor =
                getInstrumentation().getContext().getContentResolver()
                        .openTypedAssetFileDescriptor(uri, mimeType, null, null);
        try (AutoCloseInputStream in = new AutoCloseInputStream(descriptor)) {
            return readFully(in);
        }
    }

    protected byte[] readFully(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        } finally {
            in.close();
        }
    }

    protected void writeFully(Uri uri, byte[] data) throws IOException {
        OutputStream out = getInstrumentation().getContext().getContentResolver()
                .openOutputStream(uri);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    protected boolean supportedHardware() {
        final PackageManager pm = getInstrumentation().getContext().getPackageManager();
        if (pm.hasSystemFeature("android.hardware.type.television")
                || pm.hasSystemFeature("android.hardware.type.watch")) {
            return false;
        }
        return true;
    }

    protected void assertActivityFailed() {
        final Result result = mActivity.getResult();
        assertEquals(REQUEST_CODE, result.requestCode);
        assertEquals(Activity.RESULT_CANCELED, result.resultCode);
        assertNull(result.data);
    }

    protected Intent assertActivitySucceeded(String prefix) {
        final Result result = mActivity.getResult();
        assertEquals(prefix + ": invalid request code", REQUEST_CODE, result.requestCode);
        assertEquals(prefix + ": invalid result code", Activity.RESULT_OK, result.resultCode);
        assertNotNull(prefix + ": null data on result", result.data);
        return result.data;
    }

    protected String executeShellCommand(String command) throws Exception {
        final String result = runShellCommand(getInstrumentation(), command).trim();
        Log.d(TAG, "Command '" + command + "' returned '" + result + "'");
        return result;
    }

    /**
     * Clears the DocumentsUI package data, unless test name ends on {@code DoNotClear}.
     */
    protected void clearDocumentsUi() throws Exception {
        final String testName = getName();
        if (testName.endsWith("DoNotClear")) {
            Log.d(TAG, "Not clearing DocumentsUI due to test name: " + testName);
            return;
        }
        executeShellCommand("pm clear com.android.documentsui");
    }

    private void disableImes() throws Exception {
        mDisabledImes = executeShellCommand("ime list -s").split("\n");
        for (String ime : mDisabledImes) {
            executeShellCommand("ime disable " + ime);
        }
    }

    private void enableImes() throws Exception {
        for (String ime : mDisabledImes) {
            executeShellCommand("ime enable " + ime);
        }
    }
}
