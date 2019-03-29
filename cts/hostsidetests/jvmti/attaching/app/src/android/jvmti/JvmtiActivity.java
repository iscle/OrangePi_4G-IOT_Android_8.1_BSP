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

package android.jvmti;

import android.app.Activity;
import android.os.Bundle;

import java.lang.Override;

/**
 * A specialized activity. This is separate from the other agent tests as we can't use
 * instrumentation for this.
 */
public class JvmtiActivity extends Activity {

    private final static String APP_DESCRIPTOR = "Landroid/jvmti/JvmtiApplication;";
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        System.out.println("Checking for " + APP_DESCRIPTOR);
        if (!didSeeLoadOf(APP_DESCRIPTOR)) {
            throw new IllegalStateException("Did not see the load of the application class!");
        }
    }

    private static native boolean didSeeLoadOf(String s);
}
