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

package android.view.animation.cts;

import android.app.Activity;
import android.os.Bundle;
import android.view.cts.R;

import java.util.concurrent.TimeUnit;

public class AnimationTestCtsActivity extends Activity {
    final static long VISIBLE_TIMEOUT = TimeUnit.SECONDS.toNanos(3);
    private boolean mIsVisible;

    public boolean isVisible() {
        return mIsVisible;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.anim_layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsVisible = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsVisible = false;
    }

    public boolean waitUntilVisible() throws InterruptedException {
        long start = System.nanoTime();
        while (!mIsVisible && (System.nanoTime() - start) < VISIBLE_TIMEOUT) {
            Thread.sleep(100);
        }
        return mIsVisible;
    }
}
