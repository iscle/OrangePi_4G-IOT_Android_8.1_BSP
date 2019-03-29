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

package android.content.cts;

import static org.junit.Assert.fail;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;

/**
 * To run:
 * cts-tradefed run singleCommand cts-dev -m CtsContentTestCases -t android.content.cts.ClipDescriptionTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ClipDescriptionTest {
    @UiThreadTest
    @Test
    public void testGetTimestamp() {
        final ClipboardManager clipboardManager = (ClipboardManager)
                InstrumentationRegistry.getTargetContext().getSystemService(
                        Context.CLIPBOARD_SERVICE);
        final long timestampBeforeSet = System.currentTimeMillis();
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Dummy text", "Text"));
        final long timestampAfterSet = System.currentTimeMillis();
        final long timestamp = clipboardManager.getPrimaryClipDescription().getTimestamp();
        if (timestamp < timestampBeforeSet || timestamp > timestampAfterSet) {
            fail("Value of timestamp is not as expected.\n"
                    + "timestamp before setting clip: " + logTime(timestampBeforeSet) + "\n"
                    + "timestamp after setting clip: " + logTime(timestampAfterSet) + "\n"
                    + "actual timestamp: " + logTime(timestamp) + "\n"
                    + "clipdata: " + clipboardManager.getPrimaryClip());
        }
    }

    /**
     * Convert a System.currentTimeMillis() value to a time of day value like
     * that printed in logs. MM-DD-YY HH:MM:SS.MMM
     *
     * @param millis since the epoch (1/1/1970)
     * @return String representation of the time.
     */
    public static String logTime(long millis) {
        Calendar c = Calendar.getInstance();
        if (millis >= 0) {
            c.setTimeInMillis(millis);
            return String.format("%tm-%td-%ty %tH:%tM:%tS.%tL", c, c, c, c, c, c, c);
        } else {
            return Long.toString(millis);
        }
    }
}