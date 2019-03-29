/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.intent.sender;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CopyPasteTest extends InstrumentationTestCase
        implements ClipboardManager.OnPrimaryClipChangedListener {

    private IntentSenderActivity mActivity;
    private ClipboardManager mClipboard;
    private Semaphore mNotified;

    private static String ACTION_COPY_TO_CLIPBOARD = "com.android.cts.action.COPY_TO_CLIPBOARD";

    private static String INITIAL_TEXT = "initial text";
    private static String NEW_TEXT = "sample text";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getInstrumentation().getTargetContext();
        mActivity = launchActivity(context.getPackageName(), IntentSenderActivity.class, null);
        mClipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    @Override
    public void tearDown() throws Exception {
        mActivity.finish();
        super.tearDown();
    }

    public void testCanReadAcrossProfiles() throws Exception {
        ClipData clip = ClipData.newPlainText(""/*label*/, INITIAL_TEXT);
        mClipboard.setPrimaryClip(clip);
        assertEquals(INITIAL_TEXT , getTextFromClipboard());

        askCrossProfileReceiverToCopy(NEW_TEXT);

        assertEquals(NEW_TEXT, getTextFromClipboard());
    }

    public void testCannotReadAcrossProfiles() throws Exception {
        ClipData clip = ClipData.newPlainText(""/*label*/, INITIAL_TEXT);
        mClipboard.setPrimaryClip(clip);
        assertEquals(INITIAL_TEXT , getTextFromClipboard());

        askCrossProfileReceiverToCopy(NEW_TEXT);

        String clipboardText = getTextFromClipboard();
        assertTrue("The clipboard text is " + clipboardText + " but should be <null> or "
                + INITIAL_TEXT, clipboardText == null || clipboardText.equals(INITIAL_TEXT));
    }

    public void testIsNotified() throws Exception {
        try {
            mNotified = new Semaphore(0);
            mActivity.addPrimaryClipChangedListener(this);

            askCrossProfileReceiverToCopy(NEW_TEXT);

            assertTrue(mNotified.tryAcquire(5, TimeUnit.SECONDS));
        } finally {
            mActivity.removePrimaryClipChangedListener(this);
        }
    }

    private void askCrossProfileReceiverToCopy(String text) throws Exception {
        Intent intent = new Intent(ACTION_COPY_TO_CLIPBOARD);
        intent.putExtra("extra_text", text);
        mActivity.getCrossProfileResult(intent);
    }

    private String getTextFromClipboard() {
        ClipData clip = mClipboard.getPrimaryClip();
        if (clip == null) {
            return null;
        }
        ClipData.Item item = clip.getItemAt(0);
        if (item == null) {
            return null;
        }
        CharSequence text = item.getText();
        if (text == null) {
            return null;
        }
        return text.toString();
    }


    @Override
    public void onPrimaryClipChanged() {
        mNotified.release();
    }

}
