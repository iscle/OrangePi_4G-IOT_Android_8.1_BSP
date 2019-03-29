/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.verifier.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Programs;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;

import com.android.cts.verifier.R;

import java.util.ArrayList;

public class MockTvInputSetupActivity extends Activity {
    private static final String TAG = "MockTvInputSetupActivity";

    /* package-private */ static final String CHANNEL_NUMBER = "999-0";
    /* package-private */ static final String CHANNEL_NAME = "Dummy";

    /* package-private */ static final String PROGRAM_TITLE = "Dummy Program";
    /* package-private */ static final String PROGRAM_DESCRIPTION = "Dummy Program Description";

    /* package-private */ static final String APP_LINK_TEST_KEY = "app_link_test_key";
    /* package-private */ static final String APP_LINK_TEST_VALUE = "app_link_test_value";
    private static final String APP_LINK_TEXT = "Cts App-Link Text";
    private static final long PROGRAM_LENGTH_MILLIS = 60 * 60 * 1000;
    private static final int PROGRAM_COUNT = 24;

    private static Object sLock = new Object();
    private static Pair<View, Runnable> sLaunchCallback = null;

    static void expectLaunch(View postTarget, Runnable successCallback) {
        synchronized (sLock) {
            sLaunchCallback = Pair.create(postTarget, successCallback);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            final String inputId = getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
            final Uri uri = TvContract.buildChannelsUriForInput(inputId);
            final String[] projection = { TvContract.Channels._ID };
            try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
                // If we already have channels, just finish without doing anything.
                if (cursor != null && cursor.getCount() > 0) {
                    return;
                }
            }

            // Add a channel.
            ContentValues values = new ContentValues();
            values.put(TvContract.Channels.COLUMN_INPUT_ID, inputId);
            values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, CHANNEL_NUMBER);
            values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, CHANNEL_NAME);
            values.put(TvContract.Channels.COLUMN_APP_LINK_TEXT, APP_LINK_TEXT);
            values.put(TvContract.Channels.COLUMN_APP_LINK_COLOR, Color.RED);
            values.put(TvContract.Channels.COLUMN_APP_LINK_ICON_URI,
                    "android.resource://" + getPackageName() + "/" + R.drawable.icon);
            values.put(TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI,
                    "android.resource://" + getPackageName() + "/" + R.raw.sns_texture);
            Intent appLinkIntentUri = new Intent(this, AppLinkTestActivity.class);
            appLinkIntentUri.putExtra(APP_LINK_TEST_KEY, APP_LINK_TEST_VALUE);
            appLinkIntentUri.setFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
            values.put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI,
                    appLinkIntentUri.toUri(Intent.URI_INTENT_SCHEME));

            Uri channelUri = getContentResolver().insert(uri, values);
            // If the channel's ID happens to be zero, we add another and delete the one.
            if (ContentUris.parseId(channelUri) == 0) {
                getContentResolver().delete(channelUri, null, null);
                channelUri = getContentResolver().insert(uri, values);
            }

            // Add Programs.
            values = new ContentValues();
            values.put(Programs.COLUMN_CHANNEL_ID, ContentUris.parseId(channelUri));
            values.put(Programs.COLUMN_TITLE, PROGRAM_TITLE);
            values.put(Programs.COLUMN_SHORT_DESCRIPTION, PROGRAM_DESCRIPTION);
            long nowMs = System.currentTimeMillis();
            long startTimeMs = nowMs - nowMs % PROGRAM_LENGTH_MILLIS;
            ArrayList<ContentValues> list = new ArrayList<>();
            for (int i = 0; i < PROGRAM_COUNT; ++i) {
                values.put(Programs.COLUMN_START_TIME_UTC_MILLIS, startTimeMs);
                values.put(Programs.COLUMN_END_TIME_UTC_MILLIS,
                        startTimeMs + PROGRAM_LENGTH_MILLIS);
                startTimeMs += PROGRAM_LENGTH_MILLIS;
                list.add(new ContentValues(values));
            }
            getContentResolver().bulkInsert(Programs.CONTENT_URI, list.toArray(
                    new ContentValues[0]));
        } finally {
            Pair<View, Runnable> launchCallback = null;
            synchronized (sLock) {
                launchCallback = sLaunchCallback;
                sLaunchCallback = null;
            }
            if (launchCallback != null) {
                launchCallback.first.post(launchCallback.second);
            }

            setResult(Activity.RESULT_OK);
            finish();
        }
    }
}
