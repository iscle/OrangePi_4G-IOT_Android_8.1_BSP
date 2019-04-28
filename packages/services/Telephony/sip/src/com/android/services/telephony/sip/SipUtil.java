/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.services.telephony.sip;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.phone.PhoneGlobals;
import com.android.phone.R;
import com.android.server.sip.SipService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SipUtil {
    static final String LOG_TAG = "SIP";
    static final String EXTRA_INCOMING_CALL_INTENT =
            "com.android.services.telephony.sip.incoming_call_intent";
    static final String EXTRA_PHONE_ACCOUNT =
            "com.android.services.telephony.sip.phone_account";

    private SipUtil() {
    }

    public static boolean isVoipSupported(Context context) {
        return SipManager.isVoipSupported(context) &&
                context.getResources().getBoolean(
                        com.android.internal.R.bool.config_built_in_sip_phone) &&
                context.getResources().getBoolean(
                        com.android.internal.R.bool.config_voice_capable);
    }

    static PendingIntent createIncomingCallPendingIntent(
            Context context, String sipProfileName) {
        Intent intent = new Intent(context, SipIncomingCallReceiver.class);
        intent.setAction(SipManager.ACTION_SIP_INCOMING_CALL);
        intent.putExtra(EXTRA_PHONE_ACCOUNT, SipUtil.createAccountHandle(context, sipProfileName));
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static boolean isPhoneIdle(Context context) {
        TelecomManager manager = (TelecomManager) context.getSystemService(
                Context.TELECOM_SERVICE);
        if (manager != null) {
            return !manager.isInCall();
        }
        return true;
    }

    /**
     * Creates a {@link PhoneAccountHandle} from the specified SIP profile name.
     */
    static PhoneAccountHandle createAccountHandle(Context context, String sipProfileName) {
        return new PhoneAccountHandle(
                new ComponentName(context, SipConnectionService.class), sipProfileName);
    }

    /**
     * Determines the SIP profile name for a specified {@link PhoneAccountHandle}.
     *
     * @param phoneAccountHandle The {@link PhoneAccountHandle}.
     * @return The SIP profile name.
     */
    static String getSipProfileNameFromPhoneAccount(PhoneAccountHandle phoneAccountHandle) {
        if (phoneAccountHandle == null) {
            return null;
        }

        String sipProfileName = phoneAccountHandle.getId();
        if (TextUtils.isEmpty(sipProfileName)) {
            return null;
        }
        return sipProfileName;
    }

    /**
     * Creates a PhoneAccount for a SipProfile.
     *
     * @param context The context
     * @param profile The SipProfile.
     * @return The PhoneAccount.
     */
    static PhoneAccount createPhoneAccount(Context context, SipProfile profile) {
        // Build a URI to represent the SIP account.  Does not use SipProfile#getUriString() since
        // that prototype can include transport information which we do not want to see in the
        // phone account.
        String sipAddress = profile.getUserName() + "@" + profile.getSipDomain();
        Uri sipUri = Uri.parse(profile.getUriString());

        PhoneAccountHandle accountHandle =
                SipUtil.createAccountHandle(context, profile.getProfileName());

        final ArrayList<String> supportedUriSchemes = new ArrayList<String>();
        supportedUriSchemes.add(PhoneAccount.SCHEME_SIP);
        if (useSipForPstnCalls(context)) {
            supportedUriSchemes.add(PhoneAccount.SCHEME_TEL);
        }

        Bundle phoneAccountExtras = new Bundle();
        phoneAccountExtras.putBoolean(PhoneAccount.EXTRA_ALWAYS_USE_VOIP_AUDIO_MODE,
                true);

        PhoneAccount.Builder builder = PhoneAccount.builder(accountHandle, profile.getDisplayName())
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER
                        | PhoneAccount.CAPABILITY_MULTI_USER)
                .setAddress(sipUri)
                .setShortDescription(sipAddress)
                .setIcon(Icon.createWithResource(
                        context.getResources(), R.drawable.ic_dialer_sip_black_24dp))
                .setExtras(phoneAccountExtras)
                .setSupportedUriSchemes(supportedUriSchemes);

        return builder.build();
    }

    /**
     * Upon migration from M->N, the SIP Profile database will be moved into DE storage. This will
     * not be a problem for non-FBE enabled devices, since DE and CE storage is available at the
     * same time. This will be a problem for backup/restore, however if the SIP Profile DB is
     * restored onto a new FBE enabled device.
     *
     * Checks if the Sip Db is in DE storage. If it is, the Db is moved to CE storage and
     * deleted.
     */
    private static void possiblyMigrateSipDb(Context context) {
        SipProfileDb dbDeStorage = new SipProfileDb(context);
        dbDeStorage.accessDEStorageForMigration();
        List<SipProfile> profilesDeStorage = dbDeStorage.retrieveSipProfileList();
        if(profilesDeStorage.size() != 0) {
            Log.i(LOG_TAG, "Migrating SIP Profiles over!");
            SipProfileDb dbCeStorage = new SipProfileDb(context);
            //Perform Profile Migration
            for (SipProfile profileToMove : profilesDeStorage) {
                if (dbCeStorage.retrieveSipProfileFromName(
                        profileToMove.getProfileName()) == null) {
                    try {
                        dbCeStorage.saveProfile(profileToMove);
                    } catch (IOException e) {
                        Log.w(LOG_TAG, "Error Migrating file to CE: " +
                                profileToMove.getProfileName(), e);
                    }
                }
                Log.i(LOG_TAG, "(Migration) Deleting SIP profile: " +
                        profileToMove.getProfileName());
                try {
                    dbDeStorage.deleteProfile(profileToMove);
                } catch (IOException e) {
                    Log.w(LOG_TAG, "Error Deleting file: " +
                            profileToMove.getProfileName(), e);
                }
            }
        }
        // Delete supporting structures if they exist
        dbDeStorage.cleanupUponMigration();
    }

    /**
     * Migrates the DB files over from CE->DE storage and starts the SipService.
     */
    public static void startSipService() {
        Context phoneGlobalsContext = PhoneGlobals.getInstance();
        // Migrate SIP database from DE->CE storage if the device has just upgraded.
        possiblyMigrateSipDb(phoneGlobalsContext);
        // Wait until boot complete to start SIP so that it has access to CE storage.
        SipService.start(phoneGlobalsContext);
    }

    /**
     * Determines if the user has chosen to use SIP for PSTN calls as well as SIP calls.
     * @param context The context.
     * @return {@code True} if SIP should be used for PSTN calls.
     */
    private static boolean useSipForPstnCalls(Context context) {
        final SipPreferences sipPreferences = new SipPreferences(context);
        return sipPreferences.getSipCallOption().equals(Settings.System.SIP_ALWAYS);
    }

    /**
     * Updates SIP accounts to indicate whether they are enabled to receive incoming SIP calls.
     *
     * @param isEnabled {@code True} if receiving incoming SIP calls.
     */
    public static void useSipToReceiveIncomingCalls(Context context, boolean isEnabled) {
        SipProfileDb profileDb = new SipProfileDb(context);

        // Mark all profiles as auto-register if we are now receiving calls.
        List<SipProfile> sipProfileList = profileDb.retrieveSipProfileList();
        for (SipProfile p : sipProfileList) {
            updateAutoRegistrationFlag(p, profileDb, isEnabled);
        }
    }

    private static void updateAutoRegistrationFlag(
            SipProfile p, SipProfileDb db, boolean isEnabled) {
        SipProfile newProfile = new SipProfile.Builder(p).setAutoRegistration(isEnabled).build();

        try {
            // Note: The profile is updated, but the associated PhoneAccount is left alone since
            // the only thing that changed is the auto-registration flag, which is not part of the
            // PhoneAccount.
            db.deleteProfile(p);
            db.saveProfile(newProfile);
        } catch (Exception e) {
            Log.d(LOG_TAG, "updateAutoRegistrationFlag, exception: " + e);
        }
    }
}
