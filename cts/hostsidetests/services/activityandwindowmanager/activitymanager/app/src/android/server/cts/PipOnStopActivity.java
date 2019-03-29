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
 * limitations under the License
 */

package android.server.cts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * This activity will try and enter picture in picture when it is stopped.
 */
public class PipOnStopActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.tap_to_finish_pip_layout);
    }

    @Override
    protected void onResume() {
        super.onResume();

        startActivity(new Intent(this, PipActivity.class));
    }

    @Override
    protected void onStop() {
        super.onStop();

        try {
            enterPictureInPictureMode();
        } catch (RuntimeException e) {
            // Known failure, we expect this call to throw an exception
        }
    }
}
