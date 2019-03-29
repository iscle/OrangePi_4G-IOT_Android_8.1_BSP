/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.app.stubs;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;

public class PendingIntentStubActivity extends Activity {

    public static final int INVALIDATE = -1;
    public static final int ON_CREATE = 0;
    public static int status = INVALIDATE;

    private static boolean sCreated = false;
    private static Object sBlocker = new Object();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        synchronized (sBlocker) {
            status = ON_CREATE;
            sCreated = true;
            sBlocker.notifyAll();
        }
    }


    public static void prepare() {
        synchronized (sBlocker) {
            status = INVALIDATE;
            sCreated = false;
        }
    }

    public static boolean waitForCreate(long timeout) {
        long now = SystemClock.elapsedRealtime();
        final long endTime = now + timeout;
        synchronized (sBlocker) {
            while (!sCreated && now < endTime) {
                try {
                    sBlocker.wait(endTime - now);
                } catch (InterruptedException e) {
                }
                now = SystemClock.elapsedRealtime();
            }
            return sCreated;
        }
    }
}
