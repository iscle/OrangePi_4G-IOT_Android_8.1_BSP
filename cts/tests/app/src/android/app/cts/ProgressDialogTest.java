/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import android.app.Instrumentation;
import android.app.ProgressDialog;
import android.app.stubs.MockActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.view.Window;
import android.widget.ProgressBar;

import android.app.stubs.R;

/**
 * Test {@link ProgressDialog}.
 */
public class ProgressDialogTest extends ActivityInstrumentationTestCase2<MockActivity> {
    private final CharSequence TITLE = "title";
    private final CharSequence MESSAGE = "message";

    private boolean mCanceled;
    private Drawable mDrawable;
    private Drawable mActualDrawableNull;
    private Drawable mActualDrawable;
    private ProgressBar mProgressBar;
    private int mProgress1;
    private int mProgress2;

    private Context mContext;
    private Instrumentation mInstrumentation;
    private MockActivity mActivity;

    public ProgressDialogTest() {
        super("android.app.stubs", MockActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mCanceled = false;
        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
        mContext = mActivity;
        mDrawable = getActivity().getResources().getDrawable(
                R.drawable.yellow);
    }

    @UiThreadTest
    public void testProgressDialog1(){
        new ProgressDialog(mContext);
    }

    @UiThreadTest
    public void testProgressDialog2(){
        new ProgressDialog(mContext, R.style.Theme_AlertDialog);
    }

    @UiThreadTest
    public void testOnStartCreateStop() {
        MockProgressDialog pd = new MockProgressDialog(mContext);

        assertFalse(pd.mIsOnCreateCalled);
        assertFalse(pd.mIsOnStartCalled);
        pd.show();
        assertTrue(pd.mIsOnCreateCalled);
        assertTrue(pd.mIsOnStartCalled);

        assertFalse(pd.mIsOnStopCalled);
        pd.dismiss();
        assertTrue(pd.mIsOnStopCalled);
    }

    @UiThreadTest
    public void testShow1() {
        ProgressDialog.show(mContext, TITLE, MESSAGE);
    }

    @UiThreadTest
    public void testShow2() {
        ProgressDialog dialog = ProgressDialog.show(mContext, TITLE, MESSAGE, false);

        /*
         * note: the progress bar's style only supports indeterminate mode,
         * so can't change indeterminate
         */
        assertTrue(dialog.isIndeterminate());

        dialog = ProgressDialog.show(mContext, TITLE, MESSAGE, true);
        assertTrue(dialog.isIndeterminate());
    }

    public void testShow3() throws Throwable {
        final OnCancelListener cL = new OnCancelListener(){
            public void onCancel(DialogInterface dialog) {
                mCanceled = true;
            }
        };

        // cancelable is false
        runTestOnUiThread(new Runnable() {
            public void run() {
                ProgressDialog dialog = ProgressDialog.show(mContext, TITLE, MESSAGE, true, false);

                dialog.setOnCancelListener(cL);
                dialog.onBackPressed();
                dialog.dismiss();
            }
        });
        mInstrumentation.waitForIdleSync();

        assertFalse(mCanceled);

        // cancelable is true
        runTestOnUiThread(new Runnable() {
            public void run() {
                ProgressDialog dialog = ProgressDialog.show(mContext, TITLE, MESSAGE, true, true);
                assertFalse(mCanceled);
                dialog.setOnCancelListener(cL);
                dialog.onBackPressed();
            }
        });
        mInstrumentation.waitForIdleSync();

        assertTrue(mCanceled);
    }

    public void testShow4() throws Throwable {
        final OnCancelListener cL = new OnCancelListener(){
            public void onCancel(DialogInterface dialog) {
                mCanceled = true;
            }
        };

        // cancelable is false
        runTestOnUiThread(new Runnable() {
            public void run() {
                ProgressDialog dialog = ProgressDialog.show(
                        mContext, TITLE, MESSAGE, true, false, cL);

                dialog.onBackPressed();
                dialog.dismiss();;
            }
        });
        mInstrumentation.waitForIdleSync();

        assertFalse(mCanceled);

        // cancelable is true
        runTestOnUiThread(new Runnable() {
            public void run() {
                ProgressDialog dialog = ProgressDialog.show(
                        mContext, TITLE, MESSAGE, true, true, cL);

                assertFalse(mCanceled);
                dialog.onBackPressed();
            }
        });
        mInstrumentation.waitForIdleSync();

        assertTrue(mCanceled);
    }

    @UiThreadTest
    public void testAccessMax() {
        // progressDialog is null
        ProgressDialog progressDialog = buildDialog();
        progressDialog.setMax(2008);
        assertEquals(2008, progressDialog.getMax());

        // progressDialog is not null
        progressDialog = ProgressDialog.show(mContext, TITLE, MESSAGE);
        progressDialog.setMax(2009);
        assertEquals(2009, progressDialog.getMax());
    }

    @UiThreadTest
    public void testAccessProgress() {
        // progressDialog is null
        ProgressDialog progressDialog = buildDialog();
        progressDialog.setProgress(11);
        assertEquals(11, progressDialog.getProgress());

        /* progressDialog is not null
         * note: the progress bar's style only supports indeterminate mode,
         * so can't change progress
         */
        progressDialog = ProgressDialog.show(mContext, TITLE, MESSAGE);
        progressDialog.setProgress(12);
        assertEquals(0, progressDialog.getProgress());
    }

    @UiThreadTest
    public void testAccessSecondaryProgress() {
        // dialog is null
        ProgressDialog dialog = buildDialog();
        dialog.setSecondaryProgress(17);
        assertEquals(17, dialog.getSecondaryProgress());

        /* mProgress is not null
         * note: the progress bar's style only supports indeterminate mode,
         * so can't change secondary progress
         */
        dialog = ProgressDialog.show(mContext, TITLE, MESSAGE);
        dialog.setSecondaryProgress(18);
        assertEquals(0, dialog.getSecondaryProgress());
    }

    @UiThreadTest
    public void testSetIndeterminate() {
        // progress is null
        ProgressDialog dialog = buildDialog();
        dialog.setIndeterminate(true);
        assertTrue(dialog.isIndeterminate());
        dialog.setIndeterminate(false);
        assertFalse(dialog.isIndeterminate());

        /* mProgress is not null
         * note: the progress bar's style only supports indeterminate mode,
         * so can't change indeterminate
         */
        dialog = ProgressDialog.show(mContext, TITLE, MESSAGE);
        dialog.setIndeterminate(true);
        assertTrue(dialog.isIndeterminate());
        dialog.setIndeterminate(false);
        assertTrue(dialog.isIndeterminate());
    }

    @UiThreadTest
    public void testIncrementProgressBy() throws Throwable {
        ProgressDialog dialog = new ProgressDialog(mContext);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.show();
        dialog.setProgress(10);
        mProgress1 = dialog.getProgress();
        dialog.incrementProgressBy(60);
        mProgress2 = dialog.getProgress();
        dialog.cancel();

        assertEquals(10, mProgress1);
        assertEquals(70, mProgress2);
    }

    @UiThreadTest
    public void testIncrementSecondaryProgressBy() throws Throwable {
        ProgressDialog dialog = new ProgressDialog(mContext);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.show();
        dialog.setSecondaryProgress(10);
        mProgress1 = dialog.getSecondaryProgress();
        dialog.incrementSecondaryProgressBy(60);
        mProgress2 = dialog.getSecondaryProgress();

        assertEquals(10, mProgress1);
        assertEquals(70, mProgress2);
    }

    @UiThreadTest
    public void testSetProgressDrawable() throws Throwable {
        ProgressDialog dialog = ProgressDialog.show(mContext, TITLE, MESSAGE);
        Window w = dialog.getWindow();
        ProgressBar progressBar = (ProgressBar) w.findViewById(android.R.id.progress);

        dialog.setProgressDrawable(mDrawable);
        mActualDrawable = progressBar.getProgressDrawable();

        dialog.setProgressDrawable(null);
        mActualDrawableNull = progressBar.getProgressDrawable();
        assertEquals(mDrawable, mActualDrawable);
        assertEquals(null, mActualDrawableNull);
    }

    @UiThreadTest
    public void testSetIndeterminateDrawable() throws Throwable {
        ProgressDialog dialog = ProgressDialog.show(mContext, TITLE, MESSAGE);
        Window w = dialog.getWindow();
        mProgressBar = (ProgressBar) w.findViewById(android.R.id.progress);

        dialog.setIndeterminateDrawable(mDrawable);
        mActualDrawable = mProgressBar.getIndeterminateDrawable();
        assertEquals(mDrawable, mActualDrawable);

        dialog.setIndeterminateDrawable(null);
        mActualDrawableNull = mProgressBar.getIndeterminateDrawable();
        assertEquals(null, mActualDrawableNull);
    }

    @UiThreadTest
    public void testSetMessage() throws Throwable {
        ProgressDialog dialog = new ProgressDialog(mContext);
        dialog = new ProgressDialog(mContext);
        dialog.setMessage(MESSAGE);
        dialog.show();
        // dialog is not null
        dialog = ProgressDialog.show(mContext, TITLE, MESSAGE);
        dialog.setMessage("Chuck Norris");
    }

    @UiThreadTest
    public void testSetProgressStyle() throws Throwable {
        ProgressDialog dialog = new ProgressDialog(mContext);
        setProgressStyle(dialog, ProgressDialog.STYLE_HORIZONTAL);
        setProgressStyle(dialog, ProgressDialog.STYLE_SPINNER);
        setProgressStyle(dialog, 100);
    }

    private void setProgressStyle(ProgressDialog dialog, int style) {
        dialog.setProgressStyle(style);
        dialog.show();
        dialog.setProgress(10);
        dialog.setMax(100);
    }

    private static class MockProgressDialog extends ProgressDialog {
        public boolean mIsOnStopCalled;
        public boolean mIsOnStartCalled;
        public boolean mIsOnCreateCalled;

        public MockProgressDialog(Context context) {
            super(context);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mIsOnCreateCalled = true;
        }

        @Override
        public void onStart(){
            super.onStart();
            mIsOnStartCalled = true;
        }

        @Override
        public void onStop() {
            super.onStop();
            mIsOnStopCalled = true;
        }
    }

    private ProgressDialog buildDialog() {
        return new ProgressDialog(mContext);
    }
}
