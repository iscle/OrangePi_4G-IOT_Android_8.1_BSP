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

package android.telephony.cts;

import android.annotation.Nullable;
import android.app.IntentService;
import android.content.Intent;

/**
 * SmsReceiver, MmsReceiver, ComposeSmsActivity, HeadlessSmsSendService together make
 * this a valid SmsApplication (that can be set as the default SMS app). Although some of these
 * classes don't do anything, they are needed to make this a valid candidate for default SMS
 * app. -->
 */
public class HeadlessSmsSendService extends IntentService {
    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public HeadlessSmsSendService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

    }
}
