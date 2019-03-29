/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.alarmclock.cts;

import android.alarmclock.common.Utils;
import android.alarmclock.common.Utils.TestcaseType;

public class SnoozeAlarmTest extends AlarmClockTestBase {
    public SnoozeAlarmTest() {
        super();
    }

    public void testAll() throws Exception {
        String result = runTest(TestcaseType.SNOOZE_ALARM);
        // Since there is no way to figure out if there is a currently-firing alarm,
        // we should expect either of the 2 results:
        //      Utils.COMPLETION_RESULT
        //      Utils.ABORT_RESULT
        // For CTS test purposes, all we can do is to know that snooze-alarm intent
        // was picked up and processed by the voice_interaction_service and the associated app.
        // The above call to runTest() would have failed the test otherwise.
        assertTrue(result.equals(Utils.ABORT_RESULT) || result.equals(Utils.COMPLETION_RESULT));
    }
}
