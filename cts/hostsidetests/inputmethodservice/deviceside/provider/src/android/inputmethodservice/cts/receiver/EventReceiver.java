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

package android.inputmethodservice.cts.receiver;

import static android.inputmethodservice.cts.common.DeviceEventConstants.EXTRA_EVENT_TIME;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.cts.DeviceEvent;
import android.inputmethodservice.cts.common.EventProviderConstants.EventTableConstants;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;

public final class EventReceiver extends BroadcastReceiver {

    private static final String TAG = EventReceiver.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final Uri CONTENT_URI = Uri.parse(EventTableConstants.CONTENT_URI);

    @Override
    public void onReceive(final Context context, final Intent intent) {
        // Since {@code intent} which comes from host has no
        // {@link DeviceEventConstants#EXTRA_EVENT_TIME EXTRA_EVENT_TIME} extra, here we record the
        // time.
        if (!intent.hasExtra(EXTRA_EVENT_TIME)) {
            intent.putExtra(EXTRA_EVENT_TIME, SystemClock.uptimeMillis());
        }
        final DeviceEvent event = DeviceEvent.newEvent(intent);
        if (DEBUG) {
            Log.d(TAG, "onReceive: event=" + event);
        }
        final ContentValues values = DeviceEvent.buildContentValues(event);
        context.getContentResolver().insert(CONTENT_URI, values);
    }
}
