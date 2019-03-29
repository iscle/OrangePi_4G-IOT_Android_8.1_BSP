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
package android.content.pm.cts.shortcutmanager.throttling;

import android.app.Activity;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.content.pm.cts.shortcutmanager.common.ReplyUtil;
import android.os.Bundle;
import android.util.Log;

/**
 * Make sure when it's running shortcut manger calls are not throttled.
 */
public class MyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final String replyAction = getIntent().getStringExtra(Constants.EXTRA_REPLY_ACTION);

        Log.i(ThrottledTests.TAG, Constants.TEST_ACTIVITY_UNTHROTTLED);

        ReplyUtil.runTestAndReply(this, replyAction, () -> {
            ThrottledTests.assertCallNotThrottled(this);
        });

        finish();
    }
}
