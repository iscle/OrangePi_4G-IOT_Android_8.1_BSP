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

package com.android.cts.managedprofile;

import static android.provider.Settings.Secure.SYNC_PARENT_SOUNDS;

import android.content.ContentResolver;
import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.provider.Settings;

/**
 * Tests that managed profile ringtones work, as well as the "sync from parent profile" feature
 */
public class RingtoneSyncTest extends BaseManagedProfileTest {

    private static final int[] RINGTONE_TYPES = {
            RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION,
            RingtoneManager.TYPE_ALARM
    };

    private ContentResolver mContentResolver;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContentResolver = mContext.getContentResolver();
    }

    /**
     * Checks that the ringtone set in the settings provider and the ringtone retrieved from
     * RingtoneManager are identical.
     *
     * Used to test that the ringtone sync setting is enabled by default, and that managed profile
     * ringtones are kept in sync with parent profile ringtones, despite the setting being kept in
     * another user from the caller.
     */
    public void testRingtoneSync() throws Exception {
        // Managed profile was just created, so sync should be active by default
        assertEquals(1, Settings.Secure.getInt(mContentResolver, SYNC_PARENT_SOUNDS));

        String defaultRingtone = Settings.System.getString(mContentResolver,
                Settings.System.RINGTONE);
        String defaultNotification = Settings.System.getString(mContentResolver,
                Settings.System.NOTIFICATION_SOUND);
        String defaultAlarm = Settings.System.getString(mContentResolver,
                Settings.System.ALARM_ALERT);

        // RingtoneManager API should retrieve the same ringtones
        validateRingtoneManagerGetRingtone(defaultRingtone, RingtoneManager.TYPE_RINGTONE);
        validateRingtoneManagerGetRingtone(defaultNotification, RingtoneManager.TYPE_NOTIFICATION);
        validateRingtoneManagerGetRingtone(defaultAlarm, RingtoneManager.TYPE_ALARM);
    }

    private void validateRingtoneManagerGetRingtone(String expected, int type) {
        Uri expectedUri = (expected == null ? null : Utils.getUriWithoutUserId(
                Uri.parse(expected)));
        Uri actualRingtoneUri = Utils.getUriWithoutUserId(
                RingtoneManager.getActualDefaultRingtoneUri(mContext, type));
        assertEquals(expectedUri, actualRingtoneUri);
    }

    /*
     * Tests that setting a work ringtone disables Settings.Secure.SYNC_PARENT_SOUNDS.
     */
    private void testSoundDisableSync(int ringtoneType) throws Exception {
        Uri originalUri = RingtoneManager.getActualDefaultRingtoneUri(mContext, ringtoneType);

        // Make sure we have the rights we need to set a new ringtone.
        assertTrue(Settings.System.canWrite(mContext));

        // Explicitly set a work sound, to stop syncing ringtones between profiles.
        assertEquals(1, Settings.Secure.getInt(mContentResolver, SYNC_PARENT_SOUNDS));
        try {
            RingtoneManager.setActualDefaultRingtoneUri(mContext, ringtoneType, null);
            assertEquals(0, Settings.Secure.getInt(mContentResolver, SYNC_PARENT_SOUNDS));
            validateRingtoneManagerGetRingtone(null, ringtoneType);
        } finally {
            // Reset the setting we just changed.
            Settings.Secure.putInt(mContentResolver, SYNC_PARENT_SOUNDS, 1);
        }

        // After re-unifying, the uri should be the same as the parent's uri.
        Uri postSyncUri = RingtoneManager.getActualDefaultRingtoneUri(mContext, ringtoneType);
        assertEquals(originalUri, postSyncUri);

        // Manually disabling sync again, without changing settings, should put the ringtone uri
        // back to its earlier value of null.
        try {
            Settings.Secure.putInt(mContentResolver, SYNC_PARENT_SOUNDS, 0);
            assertNull(RingtoneManager.getActualDefaultRingtoneUri(mContext, ringtoneType));
        } finally {
            Settings.Secure.putInt(mContentResolver, SYNC_PARENT_SOUNDS, 1);
        }
    }

    public void testRingtoneDisableSync() throws Exception {
        testSoundDisableSync(RingtoneManager.TYPE_RINGTONE);
    }

    public void testNotificationDisableSync() throws Exception {
        testSoundDisableSync(RingtoneManager.TYPE_NOTIFICATION);
    }

    public void testAlarmDisableSync() throws Exception {
        testSoundDisableSync(RingtoneManager.TYPE_ALARM);
    }
}
