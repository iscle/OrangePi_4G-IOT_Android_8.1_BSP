/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.text.cts.R;
import android.text.method.KeyListener;
import android.view.KeyEvent;
import android.widget.EditText;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;

/**
 * Base class for various KeyListener tests.
 */
public abstract class KeyListenerTestCase {
    protected KeyListenerCtsActivity mActivity;
    protected Instrumentation mInstrumentation;
    protected EditText mTextView;

    @Rule
    public ActivityTestRule<KeyListenerCtsActivity> mActivityRule =
            new ActivityTestRule<>(KeyListenerCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mTextView = (EditText) mActivity.findViewById(R.id.keylistener_textview);

        PollingCheck.waitFor(5000, mActivity::hasWindowFocus);
    }

    /**
     * Synchronously sets mTextView's key listener on the UI thread.
     */
    protected void setKeyListenerSync(final KeyListener keyListener) {
        mInstrumentation.runOnMainSync(() -> mTextView.setKeyListener(keyListener));
        mInstrumentation.waitForIdleSync();
    }

    protected static KeyEvent getKey(int keycode, int metaState) {
        long currentTime = System.currentTimeMillis();
        return new KeyEvent(currentTime, currentTime, KeyEvent.ACTION_DOWN, keycode,
                0 /* repeat */, metaState);
    }
}
