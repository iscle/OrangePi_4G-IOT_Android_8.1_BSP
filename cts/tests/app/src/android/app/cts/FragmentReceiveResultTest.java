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
package android.app.cts;

import static org.mockito.Mockito.*;

import android.app.Activity;
import android.app.Fragment;
import android.app.PendingIntent;
import android.app.stubs.FragmentResultActivity;
import android.app.stubs.FragmentTestActivity;
import android.app.stubs.R;
import android.content.Intent;
import android.content.IntentSender;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;

import org.mockito.ArgumentCaptor;

/**
 * Tests Fragment's startActivityForResult and startIntentSenderForResult.
 */
public class FragmentReceiveResultTest extends
        ActivityInstrumentationTestCase2<FragmentTestActivity> {

    private FragmentTestActivity mActivity;
    private Fragment mFragment;

    public FragmentReceiveResultTest() {
        super(FragmentTestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        mFragment = attachTestFragment();
    }

    @SmallTest
    public void testStartActivityForResultOk() {
        startActivityForResult(10, Activity.RESULT_OK, "content 10");

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mFragment, times(1))
                .onActivityResult(eq(10), eq(Activity.RESULT_OK), captor.capture());
        final String data = captor.getValue()
                .getStringExtra(FragmentResultActivity.EXTRA_RESULT_CONTENT);
        assertEquals("content 10", data);
    }

    @SmallTest
    public void testStartActivityForResultCanceled() {
        startActivityForResult(20, Activity.RESULT_CANCELED, "content 20");

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mFragment, times(1))
                .onActivityResult(eq(20), eq(Activity.RESULT_CANCELED), captor.capture());
        final String data = captor.getValue()
                .getStringExtra(FragmentResultActivity.EXTRA_RESULT_CONTENT);
        assertEquals("content 20", data);
    }

    @SmallTest
    public void testStartIntentSenderForResultOk() {
        startIntentSenderForResult(30, Activity.RESULT_OK, "content 30");

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mFragment, times(1))
                .onActivityResult(eq(30), eq(Activity.RESULT_OK), captor.capture());
        final String data = captor.getValue()
                .getStringExtra(FragmentResultActivity.EXTRA_RESULT_CONTENT);
        assertEquals("content 30", data);
    }

    @SmallTest
    public void testStartIntentSenderForResultCanceled() {
        startIntentSenderForResult(40, Activity.RESULT_CANCELED, "content 40");

        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mFragment, times(1))
                .onActivityResult(eq(40), eq(Activity.RESULT_CANCELED), captor.capture());
        final String data = captor.getValue()
                .getStringExtra(FragmentResultActivity.EXTRA_RESULT_CONTENT);
        assertEquals("content 40", data);
    }

    private Fragment attachTestFragment() {
        final Fragment fragment = spy(new Fragment());
        getInstrumentation().waitForIdleSync();
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mActivity.getFragmentManager().beginTransaction()
                        .add(R.id.content, fragment)
                        .addToBackStack(null)
                        .commitAllowingStateLoss();
                mActivity.getFragmentManager().executePendingTransactions();
            }
        });
        getInstrumentation().waitForIdleSync();
        return fragment;
    }

    private void startActivityForResult(final int requestCode, final int resultCode,
            final String content) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(mActivity, FragmentResultActivity.class);
                intent.putExtra(FragmentResultActivity.EXTRA_RESULT_CODE, resultCode);
                intent.putExtra(FragmentResultActivity.EXTRA_RESULT_CONTENT, content);

                mFragment.startActivityForResult(intent, requestCode);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    private void startIntentSenderForResult(final int requestCode, final int resultCode,
            final String content) {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(mActivity, FragmentResultActivity.class);
                intent.putExtra(FragmentResultActivity.EXTRA_RESULT_CODE, resultCode);
                intent.putExtra(FragmentResultActivity.EXTRA_RESULT_CONTENT, content);

                PendingIntent pendingIntent = PendingIntent.getActivity(mActivity,
                        requestCode, intent, 0);

                try {
                    mFragment.startIntentSenderForResult(pendingIntent.getIntentSender(),
                            requestCode, null, 0, 0, 0, null);
                } catch (IntentSender.SendIntentException e) {
                    fail("IntentSender failed");
                }
            }
        });
        getInstrumentation().waitForIdleSync();
    }

}
