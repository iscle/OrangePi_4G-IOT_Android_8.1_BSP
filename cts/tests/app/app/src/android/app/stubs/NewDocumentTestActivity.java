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

package android.app.stubs;

import android.app.Activity;
import android.content.Intent;

public class NewDocumentTestActivity extends Activity {
    public static final String NOTIFY_RESUME = "android.app.stubs.NOTIFY_RESUME";
    public static final String NOTIFY_NEW_INTENT = "android.app.stubs.NOTIFY_NEW_INTENT";

    @Override
    public void onResume() {
        super.onResume();
        sendBroadcast(new Intent(NOTIFY_RESUME));
    }

    @Override
    public void onNewIntent(Intent intent) {
        sendBroadcast(new Intent(NOTIFY_NEW_INTENT));
    }
}
