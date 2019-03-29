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

package android.text.method.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.method.TransformationMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that {@link TransformationMethod} interface gets called.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class TransformationMethodTest {
    private static final int EDIT_TXT_ID = 1;

    private Instrumentation mInstrumentation;
    private CtsActivity mActivity;
    private TransformationMethod mMethod;
    private EditText mEditText;
    private Button mButton;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule = new ActivityTestRule<>(CtsActivity.class);

    @Before
    public void setup() throws Throwable {
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(1000, mActivity::hasWindowFocus);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mMethod = mock(TransformationMethod.class);
        when(mMethod.getTransformation(any(), any())).then(returnsFirstArg());

        mActivityRule.runOnUiThread(() -> {
            mEditText = new EditTextNoIme(mActivity);
            mEditText.setId(EDIT_TXT_ID);
            mEditText.setTransformationMethod(mMethod);
            mButton = new Button(mActivity);
            mButton.setFocusableInTouchMode(true);
            LinearLayout layout = new LinearLayout(mActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(mEditText, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            layout.addView(mButton, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            mActivity.setContentView(layout);
            mEditText.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        assertTrue(mEditText.isFocused());
    }

    @Test
    public void testGetTransformation() throws Throwable {
        reset(mMethod);
        when(mMethod.getTransformation(any(), any())).then(returnsFirstArg());
        mActivityRule.runOnUiThread(() -> mEditText.setText("some text"));
        mInstrumentation.waitForIdleSync();
        verify(mMethod, atLeastOnce()).getTransformation(any(), any());
    }

    @Test
    public void testOnFocusChanged() throws Throwable {
        // lose focus
        reset(mMethod);
        assertTrue(mEditText.isFocused());
        mActivityRule.runOnUiThread(() -> mButton.requestFocus());
        mInstrumentation.waitForIdleSync();
        verify(mMethod, atLeastOnce()).onFocusChanged(any(), any(), anyBoolean(), anyInt(), any());

        // gain focus
        reset(mMethod);
        assertFalse(mEditText.isFocused());
        mActivityRule.runOnUiThread(() -> mEditText.requestFocus());
        mInstrumentation.waitForIdleSync();
        assertTrue(mEditText.isFocused());
        verify(mMethod, atLeastOnce()).onFocusChanged(any(), any(), anyBoolean(), anyInt(), any());
    }
}
