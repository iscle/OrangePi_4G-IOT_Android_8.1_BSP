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

package android.text.method.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.KeyCharacterMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.android.compatibility.common.util.CtsKeyEventUtil;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Scanner;

/**
 * Test {@link PasswordTransformationMethod}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class PasswordTransformationMethodTest {
    private static final int EDIT_TXT_ID = 1;

    /** original text */
    private static final String TEST_CONTENT = "test content";

    /** text after transformation: ************(12 dots) */
    private static final String TEST_CONTENT_TRANSFORMED =
        "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022";

    private Instrumentation mInstrumentation;
    private CtsActivity mActivity;
    private int mPasswordPrefBackUp;
    private boolean isPasswordPrefSaved;
    private PasswordTransformationMethod mMethod;
    private EditText mEditText;
    private CharSequence mTransformedText;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule = new ActivityTestRule<>(CtsActivity.class);

    @Before
    public void setup() throws Throwable {
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(1000, mActivity::hasWindowFocus);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mMethod = spy(new PasswordTransformationMethod());

        mActivityRule.runOnUiThread(() -> {
            EditText editText = new EditTextNoIme(mActivity);
            editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            editText.setId(EDIT_TXT_ID);
            editText.setTransformationMethod(mMethod);
            Button button = new Button(mActivity);
            LinearLayout layout = new LinearLayout(mActivity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(editText, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            layout.addView(button, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            mActivity.setContentView(layout);
            editText.requestFocus();
        });
        mInstrumentation.waitForIdleSync();

        mEditText = (EditText) mActivity.findViewById(EDIT_TXT_ID);
        assertTrue(mEditText.isFocused());

        enableAppOps();
        savePasswordPref();
        switchShowPassword(true);
    }

    @After
    public void teardown() {
        resumePasswordPref();
    }

    private void enableAppOps() {
        UiAutomation uiAutomation = mInstrumentation.getUiAutomation();

        StringBuilder cmd = new StringBuilder();
        cmd.append("appops set ");
        cmd.append(mActivity.getPackageName());
        cmd.append(" android:write_settings allow");
        uiAutomation.executeShellCommand(cmd.toString());

        StringBuilder query = new StringBuilder();
        query.append("appops get ");
        query.append(mActivity.getPackageName());
        query.append(" android:write_settings");
        String queryStr = query.toString();

        String result = "No operations.";
        while (result.contains("No operations")) {
            ParcelFileDescriptor pfd = uiAutomation.executeShellCommand(queryStr);
            InputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
            result = convertStreamToString(inputStream);
        }
    }

    private String convertStreamToString(InputStream is) {
        try (Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    @Test
    public void testConstructor() {
        new PasswordTransformationMethod();
    }

    @Test
    public void testTextChangedCallBacks() throws Throwable {
        mActivityRule.runOnUiThread(() ->
            mTransformedText = mMethod.getTransformation(mEditText.getText(), mEditText));

        reset(mMethod);
        // 12-key support
        KeyCharacterMap keymap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
        if (keymap.getKeyboardType() == KeyCharacterMap.NUMERIC) {
            // "HELLO" in case of 12-key(NUMERIC) keyboard
            CtsKeyEventUtil.sendKeys(mInstrumentation, mEditText,
                    "6*4 6*3 7*5 DPAD_RIGHT 7*5 7*6 DPAD_RIGHT");
        }
        else {
            CtsKeyEventUtil.sendKeys(mInstrumentation, mEditText, "H E 2*L O");
        }
        verify(mMethod, atLeastOnce()).beforeTextChanged(any(), anyInt(), anyInt(), anyInt());
        verify(mMethod, atLeastOnce()).onTextChanged(any(), anyInt(), anyInt(), anyInt());
        verify(mMethod, atLeastOnce()).afterTextChanged(any());

        reset(mMethod);

        mActivityRule.runOnUiThread(() -> mEditText.append(" "));

        // the appended string will not get transformed immediately
        // "***** "
        assertEquals("\u2022\u2022\u2022\u2022\u2022 ", mTransformedText.toString());
        verify(mMethod, atLeastOnce()).beforeTextChanged(any(), anyInt(), anyInt(), anyInt());
        verify(mMethod, atLeastOnce()).onTextChanged(any(), anyInt(), anyInt(), anyInt());
        verify(mMethod, atLeastOnce()).afterTextChanged(any());

        // it will get transformed after a while
        // "******"
        PollingCheck.waitFor(() -> mTransformedText.toString()
                .equals("\u2022\u2022\u2022\u2022\u2022\u2022"));
    }

    @Test
    public void testGetTransformation() {
        PasswordTransformationMethod method = new PasswordTransformationMethod();

        assertEquals(TEST_CONTENT_TRANSFORMED,
                method.getTransformation(TEST_CONTENT, null).toString());

        CharSequence transformed = method.getTransformation(null, mEditText);
        assertNotNull(transformed);
        try {
            transformed.toString();
            fail("Should throw NullPointerException if the source is null.");
        } catch (NullPointerException e) {
            // expected
        }
    }

    @Test
    public void testGetInstance() {
        PasswordTransformationMethod method0 = PasswordTransformationMethod.getInstance();
        assertNotNull(method0);

        PasswordTransformationMethod method1 = PasswordTransformationMethod.getInstance();
        assertNotNull(method1);
        assertSame(method0, method1);
    }

    private void savePasswordPref() {
        try {
            mPasswordPrefBackUp = System.getInt(mActivity.getContentResolver(),
                    System.TEXT_SHOW_PASSWORD);
            isPasswordPrefSaved = true;
        } catch (SettingNotFoundException e) {
            isPasswordPrefSaved = false;
        }
    }

    private void resumePasswordPref() {
        if (isPasswordPrefSaved) {
            System.putInt(mActivity.getContentResolver(), System.TEXT_SHOW_PASSWORD,
                    mPasswordPrefBackUp);
        }
    }

    private void switchShowPassword(boolean on) {
        System.putInt(mActivity.getContentResolver(), System.TEXT_SHOW_PASSWORD,
                on ? 1 : 0);
    }
}
