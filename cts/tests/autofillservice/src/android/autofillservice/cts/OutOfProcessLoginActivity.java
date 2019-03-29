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

package android.autofillservice.cts;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * Simple activity showing R.layout.login_activity. Started outside of the test process.
 */
public class OutOfProcessLoginActivity extends Activity {
    private static final String LOG_TAG = OutOfProcessLoginActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Log.i(LOG_TAG, "onCreate(" + savedInstanceState + ")");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.login_activity);

        findViewById(R.id.login).setOnClickListener((v) -> {
            finish();
        });
    }

    @Override
    protected void onStop() {
        Log.i(LOG_TAG, "onStop()");
        super.onStop();

        try {
            getStoppedMarker(this).createNewFile();
        } catch (IOException e) {
            Log.e(LOG_TAG, "cannot write stopped filed");
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(LOG_TAG, "onDestroy()");
        super.onDestroy();
    }

    /**
     * Get the file that signals that the activity has entered {@link Activity#onStop()}.
     *
     * @param context Context of the app
     * @return The marker file that is written onStop()
     */
    @NonNull public static File getStoppedMarker(@NonNull Context context) {
        return new File(context.getFilesDir(), "stopped");
    }
}
