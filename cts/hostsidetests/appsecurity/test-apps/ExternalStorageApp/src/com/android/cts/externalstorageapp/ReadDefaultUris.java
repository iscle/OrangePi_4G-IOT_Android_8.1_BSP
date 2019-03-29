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

package com.android.cts.externalstorageapp;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.Settings;
import android.test.AndroidTestCase;

/** Verify system default URI's can be read without READ_EXTERNAL_STORAGE permission. */
public class ReadDefaultUris extends AndroidTestCase {

    private AudioManager mAudioManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public void testPlayDefaultUris() throws Exception {
        final long timeToPlayMs = 1000;
        playUri(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                timeToPlayMs,
                AudioAttributes.USAGE_NOTIFICATION,
                AudioAttributes.CONTENT_TYPE_SONIFICATION);
        playUri(
                Settings.System.DEFAULT_RINGTONE_URI,
                timeToPlayMs,
                AudioAttributes.USAGE_NOTIFICATION_RINGTONE,
                AudioAttributes.CONTENT_TYPE_SONIFICATION);
        playUri(
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                timeToPlayMs,
                AudioAttributes.USAGE_ALARM,
                AudioAttributes.CONTENT_TYPE_SONIFICATION);
    }

    private void playUri(final Uri uri, long timeToPlayMs, int usage, int contentType)
            throws Exception {
        MediaPlayer mp = new MediaPlayer();
        assertNotNull(mp);
        mp.setDataSource(mContext, uri);
        mp.setAudioAttributes(
                new AudioAttributes.Builder().setUsage(usage).setContentType(contentType).build());
        mp.prepare();
        mp.start();
        Thread.sleep(timeToPlayMs);
        mp.stop();
        mp.release();
        Thread.sleep(timeToPlayMs);
        assertFalse(mAudioManager.isMusicActive());
    }
}
