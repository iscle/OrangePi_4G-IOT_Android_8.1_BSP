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

package android.provider.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.provider.FontRequest;
import android.provider.FontsContract;
import android.provider.FontsContract.Columns;
import android.provider.FontsContract.FontFamilyResult;
import android.provider.FontsContract.FontInfo;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FontsContractTest {
    private static final String AUTHORITY = "android.provider.fonts.cts.font";
    private static final String PACKAGE = "android.provider.cts";

    // Signature to be used for authentication to access content provider.
    // In this test case, the content provider and consumer live in the same package, self package's
    // signature works.
    private static List<List<byte[]>> SIGNATURE;
    static {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(
                    context.getPackageName(), PackageManager.GET_SIGNATURES);
            ArrayList<byte[]> out = new ArrayList<>();
            for (Signature sig : info.signatures) {
                out.add(sig.toByteArray());
            }
            SIGNATURE = new ArrayList<>();
            SIGNATURE.add(out);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Instrumentation mInstrumentation;
    private Context mContext;
    private Handler mMainThreadHandler;

    @Before
    public void setUp() throws Exception {
        mMainThreadHandler = new Handler(Looper.getMainLooper());
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
        MockFontProvider.prepareFontFiles(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void tearDown() {
        mMainThreadHandler = null;
        MockFontProvider.cleanUpFontFiles(
                InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    private static class TestCallback extends FontsContract.FontRequestCallback {
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private Typeface mTypeface;
        @GuardedBy("mLock")
        private int mFailedReason;
        @GuardedBy("mLock")
        private int mSuccessCallCount;
        @GuardedBy("mLock")
        private int mFailedCallCount;

        public void onTypefaceRetrieved(Typeface typeface) {
            synchronized(mLock) {
                mTypeface = typeface;
                mSuccessCallCount++;
            }
        }

        public void onTypefaceRequestFailed(int reason) {
            synchronized(mLock) {
                mFailedCallCount++;
                mFailedReason = reason;
            }
        }

        public Typeface getTypeface() {
            synchronized(mLock) {
                return mTypeface;
            }
        }

        public int getFailedReason() {
            synchronized(mLock) {
                return mFailedReason;
            }
        }

        public int getSuccessCallCount() {
            synchronized(mLock) {
                return mSuccessCallCount;
            }
        }

        public int getFailedCallCount() {
            synchronized(mLock) {
                return mFailedCallCount;
            }
        }
    }


    @Test
    public void requestFont() throws NameNotFoundException {
        FontRequest request = new FontRequest(AUTHORITY, PACKAGE,
                MockFontProvider.SINGLE_FONT_FAMILY_QUERY, SIGNATURE);
        TestCallback callback = new TestCallback();

        mInstrumentation.runOnMainSync(() -> FontsContract.requestFonts(
                mContext, request, mMainThreadHandler, null, callback));

        mInstrumentation.waitForIdleSync();
        assertEquals(1, callback.getSuccessCallCount());
        assertEquals(0, callback.getFailedCallCount());
        assertNotNull(callback.mTypeface);
    }

    @Test
    public void requestFontNegativeErrorCode() throws NameNotFoundException {
        FontRequest request = new FontRequest(AUTHORITY, PACKAGE,
                MockFontProvider.NEGATIVE_ERROR_CODE_QUERY, SIGNATURE);
        TestCallback callback = new TestCallback();

        mInstrumentation.runOnMainSync(() -> FontsContract.requestFonts(
                mContext, request, mMainThreadHandler, null, callback));

        mInstrumentation.waitForIdleSync();
        assertNull(callback.mTypeface);
        assertEquals(1, callback.getFailedCallCount());
        assertEquals(0, callback.getSuccessCallCount());
        assertEquals(FontsContract.FontRequestCallback.FAIL_REASON_FONT_LOAD_ERROR,
                callback.getFailedReason());
    }

    @Test
    public void querySingleFont() throws NameNotFoundException {
        FontRequest request = new FontRequest(AUTHORITY, PACKAGE,
                MockFontProvider.SINGLE_FONT_FAMILY_QUERY, SIGNATURE);
        FontFamilyResult result = FontsContract.fetchFonts(
                mContext, null /* cancellation signal */, request);
        assertNotNull(result);
        assertEquals(FontFamilyResult.STATUS_OK, result.getStatusCode());

        FontInfo[] fonts = result.getFonts();
        assertEquals(1, fonts.length);
        FontInfo font = fonts[0];
        assertNotNull(font.getUri());
        assertEquals(Columns.RESULT_CODE_OK, font.getResultCode());
        assertNotNull(FontsContract.buildTypeface(mContext, null /* cancellation signal */, fonts));
    }

    @Test
    public void queryMultipleFont() throws NameNotFoundException {
        FontRequest request = new FontRequest(AUTHORITY, PACKAGE,
                MockFontProvider.MULTIPLE_FAMILY_QUERY, SIGNATURE);
        FontFamilyResult result = FontsContract.fetchFonts(
                mContext, null /* cancellation signal */, request);
        assertNotNull(result);
        assertEquals(FontFamilyResult.STATUS_OK, result.getStatusCode());

        FontInfo[] fonts = result.getFonts();
        assertEquals(4, fonts.length);
        for (FontInfo font: fonts) {
            assertNotNull(font.getUri());
            assertEquals(Columns.RESULT_CODE_OK, font.getResultCode());
        }
        assertNotNull(FontsContract.buildTypeface(mContext, null /* cancellation signal */, fonts));
    }

    @Test
    public void queryAttributes() throws NameNotFoundException {
        FontRequest request = new FontRequest(AUTHORITY, PACKAGE,
                MockFontProvider.ALL_ATTRIBUTE_VALUES_QUERY, SIGNATURE);
        FontFamilyResult result = FontsContract.fetchFonts(
                mContext, null /* cancellation signal */, request);
        assertNotNull(result);
        assertEquals(FontFamilyResult.STATUS_OK, result.getStatusCode());

        FontInfo[] fonts = result.getFonts();
        assertEquals(2, fonts.length);
        FontInfo font = fonts[0];
        assertNotNull(font.getUri());
        assertEquals(400, font.getWeight());
        assertEquals(1, font.getAxes().length);
        assertEquals(0, font.getTtcIndex());
        assertFalse(font.isItalic());
        assertEquals(Columns.RESULT_CODE_OK, font.getResultCode());

        font = fonts[1];
        assertNotNull(font.getUri());
        assertEquals(700, font.getWeight());
        assertEquals(1, font.getAxes().length);
        assertEquals(1, font.getTtcIndex());
        assertTrue(font.isItalic());
        assertEquals(Columns.RESULT_CODE_OK, font.getResultCode());

        assertNotNull(FontsContract.buildTypeface(mContext, null /* cancellation signal */, fonts));
    }

    @Test
    public void queryNotFound() throws NameNotFoundException {
        FontRequest request = new FontRequest(AUTHORITY, PACKAGE,
                MockFontProvider.NOT_FOUND_QUERY, SIGNATURE);
        FontFamilyResult result = FontsContract.fetchFonts(
                mContext, null /* cancellation signal */, request);
        assertNotNull(result);
        assertEquals(FontFamilyResult.STATUS_OK, result.getStatusCode());

        FontInfo[] fonts = result.getFonts();
        assertEquals(1, fonts.length);
        FontInfo font = fonts[0];
        assertEquals(Columns.RESULT_CODE_FONT_NOT_FOUND, font.getResultCode());
        assertNull(FontsContract.buildTypeface(mContext, null /* cancellation signal */, fonts));
    }

    @Test
    public void queryUnavailable() throws NameNotFoundException {
        FontRequest request = new FontRequest(AUTHORITY, PACKAGE,
                MockFontProvider.UNAVAILABLE_QUERY, SIGNATURE);
        FontFamilyResult result = FontsContract.fetchFonts(
                mContext, null /* cancellation signal */, request);
        assertNotNull(result);
        assertEquals(FontFamilyResult.STATUS_OK, result.getStatusCode());

        FontInfo[] fonts = result.getFonts();
        assertEquals(1, fonts.length);
        FontInfo font = fonts[0];
        assertEquals(Columns.RESULT_CODE_FONT_UNAVAILABLE, font.getResultCode());
        assertNull(FontsContract.buildTypeface(mContext, null /* cancellation signal */, fonts));
    }

    @Test
    public void queryMalformed() throws NameNotFoundException {
        FontRequest request = new FontRequest(AUTHORITY, PACKAGE,
                MockFontProvider.MALFORMED_QUERY, SIGNATURE);
        FontFamilyResult result = FontsContract.fetchFonts(
                mContext, null /* cancellation signal */, request);
        assertNotNull(result);
        assertEquals(FontFamilyResult.STATUS_OK, result.getStatusCode());

        FontInfo[] fonts = result.getFonts();
        assertEquals(1, fonts.length);
        FontInfo font = fonts[0];
        assertEquals(Columns.RESULT_CODE_MALFORMED_QUERY, font.getResultCode());
        assertNull(FontsContract.buildTypeface(mContext, null /* cancellation signal */, fonts));
    }

    @Test
    public void queryMultipleOneNotFound() throws NameNotFoundException {
        FontRequest request = new FontRequest(AUTHORITY, PACKAGE,
                MockFontProvider.NOT_FOUND_SECOND_QUERY, SIGNATURE);
        FontFamilyResult result = FontsContract.fetchFonts(
                mContext, null /* cancellation signal */, request);
        assertNotNull(result);
        assertEquals(FontFamilyResult.STATUS_OK, result.getStatusCode());

        FontInfo[] fonts = result.getFonts();
        assertEquals(2, fonts.length);
        FontInfo font = fonts[0];
        assertEquals(Columns.RESULT_CODE_OK, font.getResultCode());
        FontInfo font2 = fonts[1];
        assertEquals(Columns.RESULT_CODE_FONT_NOT_FOUND, font2.getResultCode());
        assertNotNull(FontsContract.buildTypeface(mContext, null /* cancellation signal */, fonts));
    }

    @Test
    public void queryMandatoryFieldsOnly() throws NameNotFoundException {
        FontRequest request = new FontRequest(AUTHORITY, PACKAGE,
                MockFontProvider.MANDATORY_FIELDS_ONLY_QUERY, SIGNATURE);
        FontFamilyResult result = FontsContract.fetchFonts(
                mContext, null /* cancellation signal */, request);
        assertNotNull(result);
        assertEquals(FontFamilyResult.STATUS_OK, result.getStatusCode());

        FontInfo[] fonts = result.getFonts();
        assertEquals(1, fonts.length);
        FontInfo font = fonts[0];
        assertNotNull(font.getUri());
        assertEquals(400, font.getWeight());
        assertNull(font.getAxes());
        assertFalse(font.isItalic());
        assertEquals(Columns.RESULT_CODE_OK, font.getResultCode());
        assertNotNull(FontsContract.buildTypeface(mContext, null /* cancellation signal */, fonts));
    }

    @Test
    public void restrictContextRejection() throws NameNotFoundException {
        Context restrictedContext = mContext.createPackageContext(
                PACKAGE, Context.CONTEXT_RESTRICTED);

        FontRequest request = new FontRequest(AUTHORITY, PACKAGE,
                MockFontProvider.SINGLE_FONT_FAMILY_QUERY, SIGNATURE);

        // Rejected if restricted context is used.
        FontFamilyResult result = FontsContract.fetchFonts(
                restrictedContext, null /* cancellation signal */, request);
        assertEquals(FontFamilyResult.STATUS_REJECTED, result.getStatusCode());

        // Even if you have a result, buildTypeface should fail with restricted context.
        result = FontsContract.fetchFonts(mContext, null /* cancellation signal */, request);
        assertEquals(FontFamilyResult.STATUS_OK, result.getStatusCode());
        assertNull(FontsContract.buildTypeface(
                restrictedContext, null /* cancellation signal */, result.getFonts()));
    }
}
