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
 * limitations under the License
 */

package android.provider.cts;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.provider.CallLog;
import android.test.InstrumentationTestCase;

public class CallLogTest extends InstrumentationTestCase {

    private static final String TEST_NUMBER = "5625698388";
    private static final long CONTENT_RESOLVER_TIMEOUT_MS = 5000;

    public void testGetLastOutgoingCall() {
        // Clear call log and ensure there are no outgoing calls
        Context context = getInstrumentation().getContext();
        ContentResolver resolver = context.getContentResolver();
        resolver.delete(CallLog.Calls.CONTENT_URI, null, null);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return "";
                    }

                    @Override
                    public Object actual() {
                        return CallLog.Calls.getLastOutgoingCall(context);
                    }
                },
                CONTENT_RESOLVER_TIMEOUT_MS,
                "getLastOutgoingCall did not return empty after CallLog was cleared"
        );

        // Add a single call and verify it returns as last outgoing call
        ContentValues values = new ContentValues();
        values.put(CallLog.Calls.NUMBER, TEST_NUMBER);
        values.put(CallLog.Calls.TYPE, Integer.valueOf(CallLog.Calls.OUTGOING_TYPE));
        values.put(CallLog.Calls.DATE, Long.valueOf(0 /*start time*/));
        values.put(CallLog.Calls.DURATION, Long.valueOf(5 /*call duration*/));

        resolver.insert(CallLog.Calls.CONTENT_URI, values);

        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return TEST_NUMBER;
                    }

                    @Override
                    public Object actual() {
                        return CallLog.Calls.getLastOutgoingCall(context);
                    }
                },
                CONTENT_RESOLVER_TIMEOUT_MS,
                "getLastOutgoingCall did not return " + TEST_NUMBER + " as expected"
        );
    }

    private void waitUntilConditionIsTrueOrTimeout(Condition condition, long timeout,
            String description) {
        final long start = System.currentTimeMillis();
        while (!condition.expected().equals(condition.actual())
                && System.currentTimeMillis() - start < timeout) {
            sleep(50);
        }
        assertEquals(description, condition.expected(), condition.actual());
    }

    protected interface Condition {
        Object expected();
        Object actual();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }
}
