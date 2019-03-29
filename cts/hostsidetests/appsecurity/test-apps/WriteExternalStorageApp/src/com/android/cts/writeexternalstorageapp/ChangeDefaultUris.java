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

package com.android.cts.writeexternalstorageapp;

import android.content.ContentValues;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.test.AndroidTestCase;
import com.android.compatibility.common.util.FileCopyHelper;
import java.io.File;

/** Sets up providers and notifications using external storage. */
public class ChangeDefaultUris extends AndroidTestCase {

    /** Unique title for provider insert and delete. */
    private static final String RINGER_TITLE = "CTS ringer title";

    public void testChangeDefaultUris() throws Exception {
        File mediaFile =
                new File(
                        Environment.getExternalStorageDirectory(),
                        "ringer" + System.currentTimeMillis() + ".mp3");
        FileCopyHelper copier = new FileCopyHelper(mContext);
        copier.copyToExternalStorage(R.raw.ringer, mediaFile);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, mediaFile.getPath());
        values.put(MediaStore.MediaColumns.TITLE, RINGER_TITLE);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp3");
        values.put(MediaStore.Audio.AudioColumns.ARTIST, "CTS ringer artist");
        values.put(MediaStore.Audio.AudioColumns.IS_RINGTONE, true);
        values.put(MediaStore.Audio.AudioColumns.IS_NOTIFICATION, true);
        values.put(MediaStore.Audio.AudioColumns.IS_ALARM, true);
        values.put(MediaStore.Audio.AudioColumns.IS_MUSIC, false);

        Uri uri =
                mContext.getContentResolver()
                        .insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);

        RingtoneManager.setActualDefaultRingtoneUri(mContext, RingtoneManager.TYPE_RINGTONE, uri);
        RingtoneManager.setActualDefaultRingtoneUri(mContext, RingtoneManager.TYPE_ALARM, uri);
        RingtoneManager.setActualDefaultRingtoneUri(
                mContext, RingtoneManager.TYPE_NOTIFICATION, uri);
    }

    /** Resets and cleans up to a valid state. This method must not fail. */
    public void testResetDefaultUris() {
        mContext.getContentResolver()
                .delete(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.MediaColumns.TITLE + " = ?",
                        new String[] {RINGER_TITLE});

        Uri uri = RingtoneManager.getValidRingtoneUri(mContext);
        RingtoneManager.setActualDefaultRingtoneUri(mContext, RingtoneManager.TYPE_RINGTONE, uri);
        RingtoneManager.setActualDefaultRingtoneUri(mContext, RingtoneManager.TYPE_ALARM, uri);
        RingtoneManager.setActualDefaultRingtoneUri(
                mContext, RingtoneManager.TYPE_NOTIFICATION, uri);
    }
}
