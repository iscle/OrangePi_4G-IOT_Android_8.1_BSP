/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.permission.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.telephony.TelephonyManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Collections;

/**
 * Test the non-location-related functionality of TelephonyManager.
 */
@RunWith(AndroidJUnit4.class)
public class TelephonyManagerPermissionTest {

    private boolean mHasTelephony;
    TelephonyManager mTelephonyManager = null;
    private AudioManager mAudioManager;

    @Before
    public void setUp() throws Exception {
        mHasTelephony = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY);
        mTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        assertNotNull(mTelephonyManager);
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        assertNotNull(mAudioManager);
    }

    /**
     * Verify that TelephonyManager.getDeviceId requires Permission.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE}.
     */
    @Test
    public void testGetDeviceId() {
        if (!mHasTelephony) {
            return;
        }

        try {
            String id = mTelephonyManager.getDeviceId();
            fail("Got device ID: " + id);
        } catch (SecurityException e) {
            // expected
        }
        try {
            String id = mTelephonyManager.getDeviceId(0);
            fail("Got device ID: " + id);
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verify that TelephonyManager.getLine1Number requires Permission.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE}.
     */
    @Test
    public void testGetLine1Number() {
        if (!mHasTelephony) {
            return;
        }

        try {
            String nmbr = mTelephonyManager.getLine1Number();
            fail("Got line 1 number: " + nmbr);
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verify that TelephonyManager.getSimSerialNumber requires Permission.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE}.
     */
    @Test
    public void testGetSimSerialNumber() {
        if (!mHasTelephony) {
            return;
        }

        try {
            String nmbr = mTelephonyManager.getSimSerialNumber();
            fail("Got SIM serial number: " + nmbr);
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verify that TelephonyManager.getSubscriberId requires Permission.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE}.
     */
    @Test
    public void testGetSubscriberId() {
        if (!mHasTelephony) {
            return;
        }

        try {
            String sid = mTelephonyManager.getSubscriberId();
            fail("Got subscriber id: " + sid);
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verify that TelephonyManager.getVoiceMailNumber requires Permission.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE}.
     */
    @Test
    public void testVoiceMailNumber() {
        if (!mHasTelephony) {
            return;
        }

        try {
            String vmnum = mTelephonyManager.getVoiceMailNumber();
            fail("Got voicemail number: " + vmnum);
        } catch (SecurityException e) {
            // expected
        }
    }
    /**
     * Verify that AudioManager.setMode requires Permission.
     * <p>
     * Requires Permissions:
     * {@link android.Manifest.permission#MODIFY_AUDIO_SETTINGS} and
     * {@link android.Manifest.permission#MODIFY_PHONE_STATE} for
     * {@link AudioManager#MODE_IN_CALL}.
     */
    @Test
    public void testSetMode() {
        if (!mHasTelephony) {
            return;
        }
        int audioMode = mAudioManager.getMode();
        mAudioManager.setMode(AudioManager.MODE_IN_CALL);
        assertEquals(audioMode, mAudioManager.getMode());
    }

    /**
     * Verify that TelephonyManager.setDataEnabled requires Permission.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#MODIFY_PHONE_STATE}.
     */
    @Test
    public void testSetDataEnabled() {
        if (!mHasTelephony) {
            return;
        }
        try {
            mTelephonyManager.setDataEnabled(false);
            fail("Able to set data enabled");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verify that Telephony related broadcasts are protected.
     */
    @Test
    public void testProtectedBroadcasts() {
        if (!mHasTelephony) {
            return;
        }
        try {
            Intent intent = new Intent("android.intent.action.SIM_STATE_CHANGED");
            getContext().sendBroadcast(intent);
            fail("SecurityException expected!");
        } catch (SecurityException e) {}
        try {
            Intent intent = new Intent("android.intent.action.SERVICE_STATE");
            getContext().sendBroadcast(intent);
            fail("SecurityException expected!");
        } catch (SecurityException e) {}
        try {
            Intent intent = new Intent("android.telephony.action.DEFAULT_SUBSCRIPTION_CHANGED");
            getContext().sendBroadcast(intent);
            fail("SecurityException expected!");
        } catch (SecurityException e) {}
        try {
            Intent intent = new Intent(
                    "android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
            getContext().sendBroadcast(intent);
            fail("SecurityException expected!");
        } catch (SecurityException e) {}
        try {
            Intent intent = new Intent(
                    "android.telephony.action.DEFAULT_SMS_SUBSCRIPTION_CHANGED");
            getContext().sendBroadcast(intent);
            fail("SecurityException expected!");
        } catch (SecurityException e) {}
        try {
            Intent intent = new Intent(
                    "android.intent.action.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED");
            getContext().sendBroadcast(intent);
            fail("SecurityException expected!");
        } catch (SecurityException e) {}
        try {
            Intent intent = new Intent("android.intent.action.SIG_STR");
            getContext().sendBroadcast(intent);
            fail("SecurityException expected!");
        } catch (SecurityException e) {}
        try {
            Intent intent = new Intent("android.provider.Telephony.SECRET_CODE");
            getContext().sendBroadcast(intent);
            fail("SecurityException expected!");
        } catch (SecurityException e) {}
    }

    /**
     * Verify that TelephonyManager.getImei requires Permission.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE}.
     */
    @Test
    public void testGetImei() {
        if (!mHasTelephony) {
            return;
        }

        try {
            String imei = mTelephonyManager.getImei();
            fail("Got IMEI: " + imei);
        } catch (SecurityException e) {
            // expected
        }
        try {
            String imei = mTelephonyManager.getImei(0);
            fail("Got IMEI: " + imei);
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verify that TelephonyManager.setAllowedCarriers requires Permission.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#MODIFY_PHONE_STATE}.
     */
    @Test
    public void testSetAllowedCarriers() {
        if (!mHasTelephony
                || !getContext().getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_TELEPHONY_CARRIERLOCK)) {
            return;
        }
        try {
            mTelephonyManager.setAllowedCarriers(0, Collections.emptyList());
            fail("Able to set allowed carriers");
        } catch (SecurityException e) {
            // expected
        }
    }

    /**
     * Verify that TelephonyManager.getAllowedCarriers requires Permission.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#READ_PRIVILEGED_PHONE_STATE}.
     */
    @Test
    public void testGetAllowedCarriers() {
        if (!mHasTelephony
                || !getContext().getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_TELEPHONY_CARRIERLOCK)) {
            return;
        }
        try {
            mTelephonyManager.getAllowedCarriers(0);
            fail("Able to get allowed carriers");
        } catch (SecurityException e) {
            // expected
        }
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }
}
