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

package com.android.services.telephony.sip;

import android.content.Context;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the {@link PhoneAccount} entries for SIP calling.
 */
public final class SipAccountRegistry {
    private final class AccountEntry {
        private final SipProfile mProfile;

        AccountEntry(SipProfile profile) {
            mProfile = profile;
        }

        SipProfile getProfile() {
            return mProfile;
        }

        /**
         * Starts the SIP service associated with the SIP profile.
         *
         * @param sipManager The SIP manager.
         * @param context The context.
         * @param isReceivingCalls {@code True} if the sip service is being started to make and
         *          receive calls.  {@code False} if the sip service is being started only for
         *          outgoing calls.
         * @return {@code True} if the service started successfully.
         */
        boolean startSipService(SipManager sipManager, Context context, boolean isReceivingCalls) {
            if (VERBOSE) log("startSipService, profile: " + mProfile);
            try {
                // Stop the Sip service for the profile if it is already running.  This is important
                // if we are changing the state of the "receive calls" option.
                sipManager.close(mProfile.getUriString());

                // Start the sip service for the profile.
                if (isReceivingCalls) {
                    sipManager.open(
                            mProfile,
                            SipUtil.createIncomingCallPendingIntent(context,
                                    mProfile.getProfileName()),
                            null);
                } else {
                    sipManager.open(mProfile);
                }
                return true;
            } catch (SipException e) {
                log("startSipService, profile: " + mProfile.getProfileName() +
                        ", exception: " + e);
            }
            return false;
        }

        /**
         * Stops the SIP service associated with the SIP profile.  The {@code SipAccountRegistry} is
         * informed when the service has been stopped via an intent which triggers
         * {@link SipAccountRegistry#removeSipProfile(String)}.
         *
         * @param sipManager The SIP manager.
         * @return {@code True} if stop was successful.
         */
        boolean stopSipService(SipManager sipManager) {
            try {
                sipManager.close(mProfile.getUriString());
                return true;
            } catch (Exception e) {
                log("stopSipService, stop failed for profile: " + mProfile.getUriString() +
                        ", exception: " + e);
            }
            return false;
        }
    }

    private static final String PREFIX = "[SipAccountRegistry] ";
    private static final boolean VERBOSE = false; /* STOP SHIP if true */
    private static final SipAccountRegistry INSTANCE = new SipAccountRegistry();

    private final List<AccountEntry> mAccounts = new CopyOnWriteArrayList<>();

    private SipAccountRegistry() {}

    public static SipAccountRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Sets up the Account registry and performs any upgrade operations before it is used.
     */
    public void setup(Context context) {
        verifyAndPurgeInvalidPhoneAccounts(context);
        startSipProfilesAsync(context, (String) null, false);
    }

    /**
     * Checks the existing SIP phone {@link PhoneAccount}s registered with telecom and deletes any
     * invalid accounts.
     *
     * @param context The context.
     */
    void verifyAndPurgeInvalidPhoneAccounts(Context context) {
        TelecomManager telecomManager = TelecomManager.from(context);
        SipProfileDb profileDb = new SipProfileDb(context);
        List<PhoneAccountHandle> accountHandles = telecomManager.getPhoneAccountsSupportingScheme(
                PhoneAccount.SCHEME_SIP);

        for (PhoneAccountHandle accountHandle : accountHandles) {
            String profileName = SipUtil.getSipProfileNameFromPhoneAccount(accountHandle);
            SipProfile profile = profileDb.retrieveSipProfileFromName(profileName);
            if (profile == null) {
                log("verifyAndPurgeInvalidPhoneAccounts, deleting account: " + accountHandle);
                telecomManager.unregisterPhoneAccount(accountHandle);
            }
        }
    }

    /**
     * Starts the SIP service for the specified SIP profile and ensures it has a valid registered
     * {@link PhoneAccount}.
     *
     * @param context The context.
     * @param sipProfileName The name of the {@link SipProfile} to start, or {@code null} for all.
     * @param enableProfile Sip account should be enabled
     */
    void startSipService(Context context, String sipProfileName, boolean enableProfile) {
        startSipProfilesAsync(context, sipProfileName, enableProfile);
    }

    /**
     * Removes a {@link SipProfile} from the account registry.  Does not stop/close the associated
     * SIP service (this method is invoked via an intent from the SipService once a profile has
     * been stopped/closed).
     *
     * @param sipProfileName Name of the SIP profile.
     */
    public void removeSipProfile(String sipProfileName) {
        AccountEntry accountEntry = getAccountEntry(sipProfileName);

        if (accountEntry != null) {
            mAccounts.remove(accountEntry);
        }
    }

    /**
     * Stops a SIP profile and un-registers its associated {@link android.telecom.PhoneAccount}.
     * Called after a SIP profile is deleted.  The {@link AccountEntry} will be removed when the
     * service has been stopped.  The {@code SipService} fires the {@code ACTION_SIP_REMOVE_PHONE}
     * intent, which triggers {@link SipAccountRegistry#removeSipProfile(String)} to perform the
     * removal.
     *
     * @param context The context.
     * @param sipProfileName Name of the SIP profile.
     */
    void stopSipService(Context context, String sipProfileName) {
        // Stop the sip service for the profile.
        AccountEntry accountEntry = getAccountEntry(sipProfileName);
        if (accountEntry != null ) {
            SipManager sipManager = SipManager.newInstance(context);
            accountEntry.stopSipService(sipManager);
        }

        // Un-register its PhoneAccount.
        PhoneAccountHandle handle = SipUtil.createAccountHandle(context, sipProfileName);
        TelecomManager.from(context).unregisterPhoneAccount(handle);
    }

    /**
     * Causes the SIP service to be restarted for all {@link SipProfile}s.  For example, if the user
     * toggles the "receive calls" option for SIP, this method handles restarting the SIP services
     * in the new mode.
     *
     * @param context The context.
     */
    public void restartSipService(Context context) {
        startSipProfiles(context, null, false);
    }

    /**
     * Performs an asynchronous call to
     * {@link SipAccountRegistry#startSipProfiles(android.content.Context, String)}, starting the
     * specified SIP profile and registering its {@link android.telecom.PhoneAccount}.
     *
     * @param context The context.
     * @param sipProfileName Name of the SIP profile.
     * @param enableProfile Sip account should be enabled.
     */
    private void startSipProfilesAsync(
            final Context context, final String sipProfileName, final boolean enableProfile) {
        if (VERBOSE) log("startSipProfiles, start auto registration");

        new Thread(new Runnable() {
            @Override
            public void run() {
                startSipProfiles(context, sipProfileName, enableProfile);
            }}
        ).start();
    }

    /**
     * Loops through all SIP accounts from the SIP database, starts each service and registers
     * each with the telecom framework. If a specific sipProfileName is specified, this will only
     * register the associated SIP account.
     *
     * @param context The context.
     * @param sipProfileName A specific SIP profile Name to start, or {@code null} to start all.
     * @param enableProfile Sip account should be enabled.
     */
    private void startSipProfiles(Context context, String sipProfileName, boolean enableProfile) {
        final SipPreferences sipPreferences = new SipPreferences(context);
        boolean isReceivingCalls = sipPreferences.isReceivingCallsEnabled();
        TelecomManager telecomManager = TelecomManager.from(context);
        SipManager sipManager = SipManager.newInstance(context);
        SipProfileDb profileDb = new SipProfileDb(context);
        List<SipProfile> sipProfileList = profileDb.retrieveSipProfileList();

        for (SipProfile profile : sipProfileList) {
            // Register a PhoneAccount for the profile and optionally enable the primary
            // profile.
            if (sipProfileName == null || sipProfileName.equals(profile.getProfileName())) {
                PhoneAccount phoneAccount = SipUtil.createPhoneAccount(context, profile);
                telecomManager.registerPhoneAccount(phoneAccount);
                if (enableProfile) {
                    telecomManager.enablePhoneAccount(phoneAccount.getAccountHandle(), true);
                }
                startSipServiceForProfile(profile, sipManager, context, isReceivingCalls);
            }
        }
    }

    /**
     * Starts the SIP service for a sip profile and saves a new {@code AccountEntry} in the
     * registry.
     *
     * @param profile The {@link SipProfile} to start.
     * @param sipManager The SIP manager.
     * @param context The context.
     * @param isReceivingCalls {@code True} if the profile should be started such that it can
     *      receive incoming calls.
     */
    private void startSipServiceForProfile(SipProfile profile, SipManager sipManager,
            Context context, boolean isReceivingCalls) {
        removeSipProfile(profile.getUriString());

        AccountEntry entry = new AccountEntry(profile);
        if (entry.startSipService(sipManager, context, isReceivingCalls)) {
            mAccounts.add(entry);
        }
    }

    /**
     * Retrieves the {@link AccountEntry} from the registry with the specified name.
     *
     * @param sipProfileName Name of the SIP profile to retrieve.
     * @return The {@link AccountEntry}, or {@code null} is it was not found.
     */
    private AccountEntry getAccountEntry(String sipProfileName) {
        for (AccountEntry entry : mAccounts) {
            if (Objects.equals(sipProfileName, entry.getProfile().getProfileName())) {
                return entry;
            }
        }
        return null;
    }

    private void log(String message) {
        Log.d(SipUtil.LOG_TAG, PREFIX + message);
    }
}
