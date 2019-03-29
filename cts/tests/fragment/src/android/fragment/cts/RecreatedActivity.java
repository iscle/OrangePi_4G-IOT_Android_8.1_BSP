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
package android.fragment.cts;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Bundle;
import android.support.test.rule.ActivityTestRule;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RecreatedActivity extends FragmentTestActivity {
    // These must be cleared after each test using clearState()
    public static RecreatedActivity sActivity;
    public static CountDownLatch sResumed;
    public static CountDownLatch sDestroyed;
    private boolean mIsResumed;

    public static void clearState() {
        sActivity = null;
        sResumed = null;
        sDestroyed = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sActivity = this;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsResumed = true;
        if (sResumed != null) {
            sResumed.countDown();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsResumed = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sDestroyed != null) {
            sDestroyed.countDown();
        }
    }

    public void waitForResume(ActivityTestRule<? extends Activity> rule) throws Throwable {
        if (mIsResumed) {
            return;
        }
        if (sResumed != null) {
            assertTrue(sResumed.await(1, TimeUnit.SECONDS));
        } else {
            rule.runOnUiThread(() -> {
                if (!mIsResumed) {
                    sResumed = new CountDownLatch(1);
                }
            });
            if (sResumed != null) {
                assertTrue(sResumed.await(1, TimeUnit.SECONDS));
                sResumed = null;
            }
        }
    }
}
