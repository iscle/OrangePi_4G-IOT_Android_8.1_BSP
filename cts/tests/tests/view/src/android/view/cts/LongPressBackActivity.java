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

package android.view.cts;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

public class LongPressBackActivity extends Activity {

    private boolean mWasPaused;
    private boolean mWasStopped;
    private boolean mSawBackDown;
    private boolean mSawBackUp;
    private boolean mSawOnBackPressed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onPause() {
        mWasPaused = true;
        super.onPause();
    }

    @Override
    protected void onStop() {
        mWasStopped = true;
        super.onStop();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && !event.isCanceled()) {
            mSawBackDown = true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mSawBackUp = true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        mSawOnBackPressed = true;
        super.onBackPressed();
    }

    public boolean wasPaused() {
        return mWasPaused;
    }

    public boolean wasStopped() {
        return mWasStopped;
    }

    public boolean sawBackDown() {
        return mSawBackDown;
    }

    public boolean sawBackUp() {
        return mSawBackUp;
    }

    public boolean sawOnBackPressed() {
        return mSawOnBackPressed;
    }
}