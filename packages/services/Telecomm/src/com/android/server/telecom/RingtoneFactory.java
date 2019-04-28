/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import android.telecom.Log;
import android.telecom.PhoneAccount;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.CallerInfo;

import java.util.List;

/**
 * Uses the incoming {@link Call}'s ringtone URI (obtained by the Contact Lookup) to obtain a
 * {@link Ringtone} from the {@link RingtoneManager} that can be played by the system during an
 * incoming call. If the ringtone URI is null, use the default Ringtone for the active user.
 */
@VisibleForTesting
public class RingtoneFactory {

    private final Context mContext;
    private final CallsManager mCallsManager;

    public RingtoneFactory(CallsManager callsManager, Context context) {
        mContext = context;
        mCallsManager = callsManager;
    }

    public Ringtone getRingtone(Call incomingCall) {
        // Use the default ringtone of the work profile if the contact is a work profile contact.
        Context userContext = isWorkContact(incomingCall) ?
                getWorkProfileContextForUser(mCallsManager.getCurrentUserHandle()) :
                getContextForUserHandle(mCallsManager.getCurrentUserHandle());
        Uri ringtoneUri = incomingCall.getRingtone();
        Ringtone ringtone = null;

        if(ringtoneUri != null && userContext != null) {
            // Ringtone URI is explicitly specified. First, try to create a Ringtone with that.
            ringtone = RingtoneManager.getRingtone(userContext, ringtoneUri);
        }
        if(ringtone == null) {
            // Contact didn't specify ringtone or custom Ringtone creation failed. Get default
            // ringtone for user or profile.
            Context contextToUse = hasDefaultRingtoneForUser(userContext) ? userContext : mContext;
            Uri defaultRingtoneUri;
            if (UserManager.get(contextToUse).isUserUnlocked(contextToUse.getUserId())) {
                defaultRingtoneUri = RingtoneManager.getActualDefaultRingtoneUri(contextToUse,
                        RingtoneManager.TYPE_RINGTONE);
            } else {
                defaultRingtoneUri = Settings.System.DEFAULT_RINGTONE_URI;
            }
            if (defaultRingtoneUri == null) {
                return null;
            }
            ringtone = RingtoneManager.getRingtone(contextToUse, defaultRingtoneUri);
        }
        if (ringtone != null) {
            ringtone.setStreamType(AudioManager.STREAM_RING);
        }
        return ringtone;
    }

    private Context getWorkProfileContextForUser(UserHandle userHandle) {
        // UserManager.getEnabledProfiles returns the enabled profiles along with the user's handle
        // itself (so we must filter out the user).
        List<UserInfo> profiles = UserManager.get(mContext).getEnabledProfiles(
                userHandle.getIdentifier());
        UserInfo workprofile = null;
        int managedProfileCount = 0;
        for (UserInfo profile : profiles) {
            UserHandle profileUserHandle = profile.getUserHandle();
            if (profileUserHandle != userHandle && profile.isManagedProfile()) {
                managedProfileCount++;
                workprofile = profile;
            }
        }
        // There may be many different types of profiles, so only count Managed (Work) Profiles.
        if(managedProfileCount == 1) {
            return getContextForUserHandle(workprofile.getUserHandle());
        }
        // There are multiple managed profiles for the associated user and we do not have enough
        // info to determine which profile is the work profile. Just use the default.
        return null;
    }

    private Context getContextForUserHandle(UserHandle userHandle) {
        if(userHandle == null) {
            return null;
        }
        try {
            return mContext.createPackageContextAsUser(mContext.getPackageName(), 0, userHandle);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w("RingtoneFactory", "Package name not found: " + e.getMessage());
        }
        return null;
    }

    private boolean hasDefaultRingtoneForUser(Context userContext) {
        if(userContext == null) {
            return false;
        }
        return !TextUtils.isEmpty(Settings.System.getStringForUser(userContext.getContentResolver(),
                Settings.System.RINGTONE, userContext.getUserId()));
    }

    private boolean isWorkContact(Call incomingCall) {
        CallerInfo contactCallerInfo = incomingCall.getCallerInfo();
        return (contactCallerInfo != null) &&
                (contactCallerInfo.userType == CallerInfo.USER_TYPE_WORK);
    }
}
